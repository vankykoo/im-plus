package com.vanky.im.message.handler;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息处理统计信息
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 消息处理器的统计信息，包括处理数量、成功率、平均耗时等
 */
@Data
public class MessageHandlerStats {
    
    /** 总处理消息数 */
    private final AtomicLong totalProcessed = new AtomicLong(0);
    
    /** 处理成功数 */
    private final AtomicLong successCount = new AtomicLong(0);
    
    /** 处理失败数 */
    private final AtomicLong failureCount = new AtomicLong(0);
    
    /** 总处理时间（毫秒） */
    private final AtomicLong totalProcessTime = new AtomicLong(0);
    
    /** 各消息类型处理统计 */
    private final Map<Integer, MessageTypeStats> messageTypeStats = new ConcurrentHashMap<>();
    
    /** 统计开始时间 */
    private final long startTime = System.currentTimeMillis();
    
    /**
     * 记录消息处理开始
     * 
     * @param messageType 消息类型
     * @return 开始时间戳
     */
    public long recordProcessStart(int messageType) {
        totalProcessed.incrementAndGet();
        getOrCreateMessageTypeStats(messageType).processCount.incrementAndGet();
        return System.currentTimeMillis();
    }
    
    /**
     * 记录消息处理成功
     * 
     * @param messageType 消息类型
     * @param startTime 开始时间戳
     */
    public void recordProcessSuccess(int messageType, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        successCount.incrementAndGet();
        totalProcessTime.addAndGet(duration);
        
        MessageTypeStats stats = getOrCreateMessageTypeStats(messageType);
        stats.successCount.incrementAndGet();
        stats.totalTime.addAndGet(duration);
        stats.lastProcessTime.set(duration);
    }
    
    /**
     * 记录消息处理失败
     * 
     * @param messageType 消息类型
     * @param startTime 开始时间戳
     */
    public void recordProcessFailure(int messageType, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        failureCount.incrementAndGet();
        totalProcessTime.addAndGet(duration);
        
        MessageTypeStats stats = getOrCreateMessageTypeStats(messageType);
        stats.failureCount.incrementAndGet();
        stats.totalTime.addAndGet(duration);
        stats.lastProcessTime.set(duration);
    }
    
    /**
     * 获取成功率
     * 
     * @return 成功率（0-1）
     */
    public double getSuccessRate() {
        long total = totalProcessed.get();
        return total > 0 ? (double) successCount.get() / total : 0.0;
    }
    
    /**
     * 获取平均处理时间
     * 
     * @return 平均处理时间（毫秒）
     */
    public double getAverageProcessTime() {
        long total = totalProcessed.get();
        return total > 0 ? (double) totalProcessTime.get() / total : 0.0;
    }
    
    /**
     * 获取运行时间
     * 
     * @return 运行时间（毫秒）
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 获取或创建消息类型统计
     * 
     * @param messageType 消息类型
     * @return 消息类型统计
     */
    private MessageTypeStats getOrCreateMessageTypeStats(int messageType) {
        return messageTypeStats.computeIfAbsent(messageType, k -> new MessageTypeStats());
    }
    
    /**
     * 消息类型统计信息
     */
    @Data
    public static class MessageTypeStats {
        /** 处理数量 */
        private final AtomicLong processCount = new AtomicLong(0);
        /** 成功数量 */
        private final AtomicLong successCount = new AtomicLong(0);
        /** 失败数量 */
        private final AtomicLong failureCount = new AtomicLong(0);
        /** 总处理时间 */
        private final AtomicLong totalTime = new AtomicLong(0);
        /** a
         * 最近一次处理时间
         */
        private final AtomicLong lastProcessTime = new AtomicLong(0);
        
        /**
         * 获取成功率
         */
        public double getSuccessRate() {
            long total = processCount.get();
            return total > 0 ? (double) successCount.get() / total : 0.0;
        }
        
        /**
         * 获取平均处理时间
         */
        public double getAverageTime() {
            long total = processCount.get();
            return total > 0 ? (double) totalTime.get() / total : 0.0;
        }
    }
}
