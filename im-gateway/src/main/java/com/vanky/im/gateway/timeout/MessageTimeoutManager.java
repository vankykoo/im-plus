package com.vanky.im.gateway.timeout;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.session.MsgSender;
import com.vanky.im.gateway.timeout.config.TimeoutConfig;
import com.vanky.im.gateway.timeout.model.TimeoutStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息超时管理器实现
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 超时管理器的门面类，封装时间轮操作和重发逻辑
 */
@Slf4j
@Component
public class MessageTimeoutManager implements TimeoutManager, TimingWheel.TaskProcessor {
    
    @Autowired
    private TimeoutConfig timeoutConfig;
    
    @Autowired
    private MsgSender msgSender;
    
    /**
     * 时间轮核心
     */
    private TimingWheel timingWheel;
    
    /**
     * 待确认消息映射，用于快速取消任务
     */
    private final ConcurrentHashMap<String, TimerTask> pendingAckMap = new ConcurrentHashMap<>();
    
    /**
     * 统计计数器
     */
    private final AtomicLong totalTasksCancelled = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong totalTasksAbandoned = new AtomicLong(0);
    
    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        if (!timeoutConfig.isEnabled()) {
            log.info("消息超时重发机制已禁用");
            return;
        }
        
        // 创建时间轮
        timingWheel = new TimingWheel(timeoutConfig, this);
        
        log.info("消息超时管理器初始化完成 - 配置: {}", timeoutConfig);
    }
    
    /**
     * 启动超时管理器
     */
    @Override
    public void start() {
        if (!timeoutConfig.isEnabled() || timingWheel == null) {
            return;
        }
        
        timingWheel.start();
        log.info("消息超时管理器启动完成");
    }
    
    /**
     * 停止超时管理器
     */
    @PreDestroy
    @Override
    public void stop() {
        if (timingWheel != null) {
            timingWheel.stop();
        }
        
        // 清理待确认映射
        pendingAckMap.clear();
        
        log.info("消息超时管理器停止完成");
    }
    
    /**
     * 添加超时任务
     */
    @Override
    public void addTask(String ackId, ChatMessage message, String userId, long timeoutMs) {
        if (!isEnabled() || ackId == null || message == null || userId == null) {
            return;
        }
        
        // 检查是否已存在相同的任务
        if (pendingAckMap.containsKey(ackId)) {
            log.warn("重复添加超时任务 - 任务ID: {}, 用户: {}", ackId, userId);
            return;
        }
        
        try {
            // 计算圈数
            long delayTicks = timeoutMs / timeoutConfig.getTickDuration();
            int cycle = (int) (delayTicks / timeoutConfig.getWheelSize());
            
            // 创建超时任务
            TimerTask task = new TimerTask(ackId, message, userId, cycle);
            
            // 添加到待确认映射
            pendingAckMap.put(ackId, task);
            
            // 添加到时间轮
            timingWheel.addTask(task, timeoutMs);
            
            log.debug("添加超时任务成功 - 任务ID: {}, 用户: {}, 超时: {}ms", ackId, userId, timeoutMs);
            
        } catch (Exception e) {
            log.error("添加超时任务失败 - 任务ID: {}, 用户: {}", ackId, userId, e);
        }
    }
    
    /**
     * 添加超时任务（使用默认超时时间）
     */
    @Override
    public void addTask(String ackId, ChatMessage message, String userId) {
        addTask(ackId, message, userId, timeoutConfig.getDefaultTimeout());
    }
    
    /**
     * 取消超时任务
     */
    @Override
    public boolean cancelTask(String ackId) {
        if (!isEnabled() || ackId == null) {
            return false;
        }
        
        // 从待确认映射中移除
        TimerTask task = pendingAckMap.remove(ackId);
        if (task == null) {
            log.debug("取消超时任务失败，任务不存在 - 任务ID: {}", ackId);
            return false;
        }
        
        // 取消任务
        task.cancel();
        
        // 更新统计
        totalTasksCancelled.incrementAndGet();
        
        log.debug("取消超时任务成功 - 任务ID: {}, 用户: {}", ackId, task.getUserId());
        return true;
    }
    
    /**
     * 处理超时任务（实现TimingWheel.TaskProcessor接口）
     */
    @Override
    public void processTimeout(TimerTask task) {
        String ackId = task.getAckId();
        String userId = task.getUserId();
        
        // 从待确认映射中移除
        pendingAckMap.remove(ackId);
        
        // 检查重试次数
        if (task.getRetryCount() >= timeoutConfig.getMaxRetryCount()) {
            // 达到最大重试次数，放弃重发
            totalTasksAbandoned.incrementAndGet();
            log.warn("消息重发达到最大次数，放弃重发 - 任务ID: {}, 用户: {}, 重试次数: {}", 
                    ackId, userId, task.getRetryCount());
            return;
        }
        
        // 增加重试次数
        task.incrementRetryCount();
        totalRetries.incrementAndGet();
        
        // 重新发送消息给客户端
        boolean sent = msgSender.sendToUser(userId, task.getMessage());
        
        if (sent) {
            // 计算下次超时时间（退避策略）
            long nextTimeout = timeoutConfig.calculateBackoffTimeout(task.getRetryCount());
            
            // 重新添加到待确认映射
            pendingAckMap.put(ackId, task);
            
            // 重新添加到时间轮
            timingWheel.addTask(task, nextTimeout);
            
            log.info("消息重发成功 - 任务ID: {}, 用户: {}, 重试次数: {}, 下次超时: {}ms", 
                    ackId, userId, task.getRetryCount(), nextTimeout);
        } else {
            // 用户离线，放弃重发
            totalTasksAbandoned.incrementAndGet();
            log.warn("消息重发失败，用户离线 - 任务ID: {}, 用户: {}, 重试次数: {}", 
                    ackId, userId, task.getRetryCount());
        }
    }
    
    /**
     * 获取统计信息
     */
    @Override
    public TimeoutStats getStats() {
        if (!isEnabled()) {
            return new TimeoutStats();
        }
        
        // 获取时间轮统计
        TimeoutStats stats = timingWheel.getStats();
        
        // 更新本地统计
        stats.setTotalTasksCancelled(totalTasksCancelled.get());
        stats.setTotalRetries(totalRetries.get());
        stats.setTotalTasksAbandoned(totalTasksAbandoned.get());
        stats.setCurrentPendingTasks(pendingAckMap.size());
        
        return stats;
    }
    
    /**
     * 检查是否启用
     */
    @Override
    public boolean isEnabled() {
        return timeoutConfig.isEnabled() && timingWheel != null;
    }
    
    /**
     * 获取当前待确认任务数
     * 
     * @return 待确认任务数
     */
    public int getPendingTaskCount() {
        return pendingAckMap.size();
    }
}
