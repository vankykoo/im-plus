package com.vanky.im.gateway.timeout.model;

import lombok.Data;

/**
 * 超时重发统计信息
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 用于监控超时重发机制的运行状态
 */
@Data
public class TimeoutStats {
    
    /**
     * 总添加任务数
     */
    private long totalTasksAdded;
    
    /**
     * 总取消任务数
     */
    private long totalTasksCancelled;
    
    /**
     * 总超时任务数
     */
    private long totalTasksTimeout;
    
    /**
     * 总重试次数
     */
    private long totalRetries;
    
    /**
     * 总放弃任务数（达到最大重试次数）
     */
    private long totalTasksAbandoned;
    
    /**
     * 当前待确认消息数
     */
    private int currentPendingTasks;
    
    /**
     * 当前时间轮tick位置
     */
    private long currentTick;
    
    /**
     * 时间轮启动时间
     */
    private long startTime;
    
    /**
     * 最后一次tick时间
     */
    private long lastTickTime;
    
    /**
     * 计算成功率
     * 
     * @return 成功率百分比
     */
    public double getSuccessRate() {
        long totalProcessed = totalTasksCancelled + totalTasksAbandoned;
        if (totalProcessed == 0) {
            return 100.0;
        }
        return (double) totalTasksCancelled / totalProcessed * 100.0;
    }
    
    /**
     * 计算平均重试次数
     * 
     * @return 平均重试次数
     */
    public double getAverageRetries() {
        long totalProcessed = totalTasksCancelled + totalTasksAbandoned;
        if (totalProcessed == 0) {
            return 0.0;
        }
        return (double) totalRetries / totalProcessed;
    }
    
    /**
     * 计算运行时长（秒）
     * 
     * @return 运行时长
     */
    public long getUptimeSeconds() {
        if (startTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - startTime) / 1000;
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        totalTasksAdded = 0;
        totalTasksCancelled = 0;
        totalTasksTimeout = 0;
        totalRetries = 0;
        totalTasksAbandoned = 0;
        currentPendingTasks = 0;
        currentTick = 0;
        startTime = System.currentTimeMillis();
        lastTickTime = 0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "TimeoutStats{" +
            "totalAdded=%d, " +
            "totalCancelled=%d, " +
            "totalTimeout=%d, " +
            "totalRetries=%d, " +
            "totalAbandoned=%d, " +
            "currentPending=%d, " +
            "successRate=%.2f%%, " +
            "avgRetries=%.2f, " +
            "uptimeSeconds=%d" +
            "}",
            totalTasksAdded,
            totalTasksCancelled,
            totalTasksTimeout,
            totalRetries,
            totalTasksAbandoned,
            currentPendingTasks,
            getSuccessRate(),
            getAverageRetries(),
            getUptimeSeconds()
        );
    }
}
