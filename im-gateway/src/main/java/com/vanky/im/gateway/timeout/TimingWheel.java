package com.vanky.im.gateway.timeout;

import com.vanky.im.gateway.timeout.config.TimeoutConfig;
import com.vanky.im.gateway.timeout.model.TimeoutStats;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 时间轮核心实现
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 基于时间轮算法的高效超时任务管理器
 */
@Slf4j
public class TimingWheel {
    
    /**
     * 时间轮槽数组
     */
    private final Slot[] wheel;
    
    /**
     * 每个tick的时间间隔（毫秒）
     */
    private final long tickDuration;
    
    /**
     * 时间轮大小
     */
    private final int wheelSize;
    
    /**
     * 时间轮掩码，用于快速计算槽位
     */
    private final int wheelMask;
    
    /**
     * 当前tick位置
     */
    private volatile long currentTick = 0;
    
    /**
     * 时间轮启动时间
     */
    private final long startTime;
    
    /**
     * 后台tick线程
     */
    private Thread tickerThread;
    
    /**
     * 运行状态
     */
    private volatile boolean running = false;
    
    /**
     * 任务处理器
     */
    private final TaskProcessor taskProcessor;
    
    /**
     * 统计信息
     */
    private final TimeoutStats stats = new TimeoutStats();
    
    /**
     * 统计计数器
     */
    private final AtomicLong totalTasksAdded = new AtomicLong(0);
    private final AtomicLong totalTasksTimeout = new AtomicLong(0);
    
    /**
     * 构造函数
     * 
     * @param config 超时配置
     * @param taskProcessor 任务处理器
     */
    public TimingWheel(TimeoutConfig config, TaskProcessor taskProcessor) {
        this.wheelSize = config.getWheelSize();
        this.wheelMask = config.getWheelMask();
        this.tickDuration = config.getTickDuration();
        this.taskProcessor = taskProcessor;
        this.startTime = System.currentTimeMillis();
        
        // 初始化时间轮槽
        this.wheel = new Slot[wheelSize];
        for (int i = 0; i < wheelSize; i++) {
            wheel[i] = new Slot();
        }
        
        // 初始化统计信息
        stats.setStartTime(startTime);
        
        log.info("时间轮初始化完成 - 槽数: {}, tick间隔: {}ms", wheelSize, tickDuration);
    }
    
    /**
     * 启动时间轮
     */
    public void start() {
        if (running) {
            log.warn("时间轮已经在运行中");
            return;
        }
        
        running = true;
        tickerThread = new Thread(this::tickLoop, "message-timeout-ticker");
        tickerThread.setDaemon(true);
        tickerThread.start();
        
        log.info("时间轮启动成功");
    }
    
    /**
     * 停止时间轮
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        if (tickerThread != null) {
            tickerThread.interrupt();
            try {
                tickerThread.join(5000); // 等待5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待时间轮线程停止时被中断");
            }
        }
        
        // 清理所有槽
        for (Slot slot : wheel) {
            slot.clear();
        }
        
        log.info("时间轮停止完成");
    }
    
    /**
     * 添加超时任务
     * 
     * @param task 超时任务
     * @param timeoutMs 超时时间（毫秒）
     */
    public void addTask(TimerTask task, long timeoutMs) {
        if (task == null || task.isCancelled()) {
            return;
        }
        
        // 计算延迟的tick数
        long delayTicks = timeoutMs / tickDuration;
        if (delayTicks == 0) {
            delayTicks = 1; // 至少延迟1个tick
        }
        
        // 计算目标tick和圈数
        long targetTick = currentTick + delayTicks;
        int targetSlot = (int) (targetTick & wheelMask);
        int cycle = (int) (delayTicks / wheelSize);
        
        // 设置任务参数
        task.setCycle(cycle);
        task.setNextExecuteTime(System.currentTimeMillis() + timeoutMs);
        
        // 添加到对应槽
        wheel[targetSlot].addTask(task);
        
        // 更新统计
        totalTasksAdded.incrementAndGet();
        
        log.debug("添加超时任务 - 任务ID: {}, 用户: {}, 超时: {}ms, 目标槽: {}, 圈数: {}", 
                task.getAckId(), task.getUserId(), timeoutMs, targetSlot, cycle);
    }
    
    /**
     * 时间轮tick循环
     */
    private void tickLoop() {
        log.info("时间轮tick线程启动");
        
        long nextTickTime = System.currentTimeMillis();
        
        while (running) {
            try {
                // 等待到下一个tick时间
                nextTickTime += tickDuration;
                long sleepTime = nextTickTime - System.currentTimeMillis();
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                
                // 执行tick
                tick();
                
            } catch (InterruptedException e) {
                log.info("时间轮tick线程被中断，准备退出");
                break;
            } catch (Exception e) {
                log.error("时间轮tick执行异常", e);
                // 继续运行，不因异常而停止
            }
        }
        
        log.info("时间轮tick线程退出");
    }
    
    /**
     * 执行一次tick
     */
    private void tick() {
        // 移动指针
        currentTick++;
        int currentSlot = (int) (currentTick & wheelMask);
        
        // 更新统计
        stats.setCurrentTick(currentTick);
        stats.setLastTickTime(System.currentTimeMillis());
        
        // 处理当前槽的任务
        Slot slot = wheel[currentSlot];
        slot.processTasks(this::handleTimeoutTask);
        
        log.debug("时间轮tick - 当前tick: {}, 槽位: {}, 槽任务数: {}", 
                currentTick, currentSlot, slot.getTaskCount());
    }
    
    /**
     * 处理超时任务
     * 
     * @param task 超时任务
     */
    private void handleTimeoutTask(TimerTask task) {
        try {
            // 更新统计
            totalTasksTimeout.incrementAndGet();
            
            // 委托给任务处理器
            taskProcessor.processTimeout(task);
            
            log.debug("处理超时任务 - 任务ID: {}, 用户: {}, 重试次数: {}", 
                    task.getAckId(), task.getUserId(), task.getRetryCount());
            
        } catch (Exception e) {
            log.error("处理超时任务异常 - 任务ID: {}, 用户: {}", 
                    task.getAckId(), task.getUserId(), e);
        }
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    public TimeoutStats getStats() {
        // 更新当前统计
        stats.setTotalTasksAdded(totalTasksAdded.get());
        stats.setTotalTasksTimeout(totalTasksTimeout.get());
        stats.setCurrentTick(currentTick);
        stats.setLastTickTime(System.currentTimeMillis());
        
        // 计算当前待处理任务数
        int currentPendingTasks = 0;
        for (Slot slot : wheel) {
            currentPendingTasks += slot.getTaskCount();
        }
        stats.setCurrentPendingTasks(currentPendingTasks);
        
        return stats;
    }
    
    /**
     * 检查时间轮是否在运行
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 任务处理器接口
     */
    @FunctionalInterface
    public interface TaskProcessor {
        void processTimeout(TimerTask task);
    }
}
