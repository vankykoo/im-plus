package com.vanky.im.gateway.timeout;

import com.vanky.im.common.protocol.ChatMessage;
import lombok.Data;

/**
 * 超时任务对象
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 封装了超时后需要执行的所有信息，会被放入时间轮的槽中
 */
@Data
public class TimerTask {
    
    /**
     * 消息的唯一ID，用于取消任务
     */
    private String ackId;
    
    /**
     * 当前重试次数
     */
    private int retryCount;
    
    /**
     * 完整的消息体，用于重发
     */
    private ChatMessage message;
    
    /**
     * 目标用户ID
     */
    private String userId;
    
    /**
     * 圈数（当超时时间超过一轮时使用）
     */
    private int cycle;
    
    /**
     * 任务创建时间
     */
    private long createTime;
    
    /**
     * 下次执行时间
     */
    private long nextExecuteTime;
    
    /**
     * 是否已取消
     */
    private volatile boolean cancelled;
    
    /**
     * 双向链表 - 前驱节点
     */
    private TimerTask prev;
    
    /**
     * 双向链表 - 后继节点
     */
    private TimerTask next;
    
    /**
     * 构造函数
     * 
     * @param ackId 消息唯一ID
     * @param message 消息体
     * @param userId 目标用户ID
     * @param cycle 圈数
     */
    public TimerTask(String ackId, ChatMessage message, String userId, int cycle) {
        this.ackId = ackId;
        this.message = message;
        this.userId = userId;
        this.cycle = cycle;
        this.retryCount = 0;
        this.createTime = System.currentTimeMillis();
        this.cancelled = false;
        this.prev = null;
        this.next = null;
    }
    
    /**
     * 取消任务
     * 将任务标记为已取消，并从链表中移除
     */
    public void cancel() {
        this.cancelled = true;
        removeFromList();
    }
    
    /**
     * 从链表中移除自己
     */
    public void removeFromList() {
        if (prev != null) {
            prev.next = next;
        }
        if (next != null) {
            next.prev = prev;
        }
        prev = null;
        next = null;
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * 检查任务是否已取消
     * 
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * 检查是否需要执行（圈数为0）
     * 
     * @return true if ready to execute
     */
    public boolean isReadyToExecute() {
        return cycle == 0 && !cancelled;
    }
    
    /**
     * 减少圈数
     */
    public void decrementCycle() {
        if (cycle > 0) {
            cycle--;
        }
    }
    
    /**
     * 获取任务运行时长（毫秒）
     * 
     * @return 运行时长
     */
    public long getRunningTime() {
        return System.currentTimeMillis() - createTime;
    }
    
    @Override
    public String toString() {
        return String.format(
            "TimerTask{ackId='%s', userId='%s', retryCount=%d, cycle=%d, cancelled=%s, runningTime=%dms}",
            ackId, userId, retryCount, cycle, cancelled, getRunningTime()
        );
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TimerTask timerTask = (TimerTask) obj;
        return ackId != null ? ackId.equals(timerTask.ackId) : timerTask.ackId == null;
    }
    
    @Override
    public int hashCode() {
        return ackId != null ? ackId.hashCode() : 0;
    }
}
