package com.vanky.im.testclient.pushpull;

import com.vanky.im.common.protocol.ChatMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * 乱序缓冲区
 * 用于存储提前到达的消息，等待空洞填补后按序处理
 *
 * @author vanky
 * @since 2025-08-05
 */
public class OutOfOrderBuffer {

    private static final Logger log = Logger.getLogger(OutOfOrderBuffer.class.getName());
    
    /** 缓冲区存储：序列号 -> 消息 */
    private final Map<Long, ChatMessage> buffer = new ConcurrentHashMap<>();
    
    /** 会话ID */
    private final String conversationId;
    
    /** 当前期望的序列号 */
    private volatile long expectedSeq;
    
    /** 缓冲区锁，用于保证操作的原子性 */
    private final ReentrantLock lock = new ReentrantLock();
    
    /** 缓冲区最大容量，防止内存溢出 */
    private static final int MAX_BUFFER_SIZE = 1000;
    
    /**
     * 构造函数
     * 
     * @param conversationId 会话ID
     * @param expectedSeq 期望的起始序列号
     */
    public OutOfOrderBuffer(String conversationId, long expectedSeq) {
        this.conversationId = conversationId;
        this.expectedSeq = expectedSeq;
        log.info("创建乱序缓冲区 - 会话ID: " + conversationId + ", 期望序列号: " + expectedSeq);
    }
    
    /**
     * 添加消息到缓冲区
     * 
     * @param message 消息
     * @param seq 消息序列号
     * @return 是否添加成功
     */
    public boolean addMessage(ChatMessage message, long seq) {
        lock.lock();
        try {
            // 检查缓冲区容量
            if (buffer.size() >= MAX_BUFFER_SIZE) {
                log.warning("乱序缓冲区已满，丢弃消息 - 会话ID: " + conversationId + ", 序列号: " + seq + ", 缓冲区大小: " + buffer.size());
                return false;
            }

            // 检查是否是重复消息
            if (buffer.containsKey(seq)) {
                log.info("重复消息，忽略 - 会话ID: " + conversationId + ", 序列号: " + seq);
                return false;
            }

            // 检查是否是过期消息（序列号小于期望值）
            if (seq < expectedSeq) {
                log.info("过期消息，忽略 - 会话ID: " + conversationId + ", 序列号: " + seq + ", 期望序列号: " + expectedSeq);
                return false;
            }

            // 添加到缓冲区
            buffer.put(seq, message);
            log.info("消息添加到乱序缓冲区 - 会话ID: " + conversationId + ", 序列号: " + seq + ", 缓冲区大小: " + buffer.size());
            
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 检查并返回可以按序处理的消息列表
     * 从期望序列号开始，返回连续的消息
     * 
     * @return 可以按序处理的消息列表
     */
    public List<ChatMessage> getOrderedMessages() {
        lock.lock();
        try {
            List<ChatMessage> orderedMessages = new ArrayList<>();
            
            // 从期望序列号开始，查找连续的消息
            long currentSeq = expectedSeq;
            while (buffer.containsKey(currentSeq)) {
                ChatMessage message = buffer.remove(currentSeq);
                orderedMessages.add(message);
                currentSeq++;
            }
            
            // 更新期望序列号
            if (!orderedMessages.isEmpty()) {
                expectedSeq = currentSeq;
                log.info("从乱序缓冲区获取连续消息 - 会话ID: " + conversationId + ", 消息数量: " + orderedMessages.size() +
                        ", 新期望序列号: " + expectedSeq + ", 剩余缓冲区大小: " + buffer.size());
            }
            
            return orderedMessages;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 更新期望序列号
     * 当通过拉取补偿机制获取到消息后，需要更新期望序列号
     * 
     * @param newExpectedSeq 新的期望序列号
     */
    public void updateExpectedSeq(long newExpectedSeq) {
        lock.lock();
        try {
            if (newExpectedSeq > expectedSeq) {
                long oldExpectedSeq = expectedSeq;
                expectedSeq = newExpectedSeq;
                log.info("更新期望序列号 - 会话ID: " + conversationId + ", 旧期望序列号: " + oldExpectedSeq + ", 新期望序列号: " + expectedSeq);

                // 清理过期的缓冲消息
                cleanupExpiredMessages();
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 清理过期的缓冲消息
     * 移除序列号小于期望序列号的消息
     */
    private void cleanupExpiredMessages() {
        Iterator<Map.Entry<Long, ChatMessage>> iterator = buffer.entrySet().iterator();
        int cleanupCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<Long, ChatMessage> entry = iterator.next();
            if (entry.getKey() < expectedSeq) {
                iterator.remove();
                cleanupCount++;
            }
        }
        
        if (cleanupCount > 0) {
            log.info("清理过期缓冲消息 - 会话ID: " + conversationId + ", 清理数量: " + cleanupCount + ", 剩余缓冲区大小: " + buffer.size());
        }
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        lock.lock();
        try {
            int size = buffer.size();
            buffer.clear();
            log.info("清空乱序缓冲区 - 会话ID: " + conversationId + ", 清理消息数量: " + size);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取缓冲区状态信息
     * 
     * @return 缓冲区状态信息
     */
    public BufferStatus getStatus() {
        lock.lock();
        try {
            return new BufferStatus(conversationId, expectedSeq, buffer.size(), 
                    buffer.isEmpty() ? 0L : Collections.min(buffer.keySet()),
                    buffer.isEmpty() ? 0L : Collections.max(buffer.keySet()));
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 缓冲区状态信息
     */
    public static class BufferStatus {
        private final String conversationId;
        private final long expectedSeq;
        private final int bufferSize;
        private final long minSeq;
        private final long maxSeq;
        
        public BufferStatus(String conversationId, long expectedSeq, int bufferSize, long minSeq, long maxSeq) {
            this.conversationId = conversationId;
            this.expectedSeq = expectedSeq;
            this.bufferSize = bufferSize;
            this.minSeq = minSeq;
            this.maxSeq = maxSeq;
        }
        
        // Getters
        public String getConversationId() { return conversationId; }
        public long getExpectedSeq() { return expectedSeq; }
        public int getBufferSize() { return bufferSize; }
        public long getMinSeq() { return minSeq; }
        public long getMaxSeq() { return maxSeq; }
        
        @Override
        public String toString() {
            return String.format("BufferStatus{conversationId='%s', expectedSeq=%d, bufferSize=%d, minSeq=%d, maxSeq=%d}",
                    conversationId, expectedSeq, bufferSize, minSeq, maxSeq);
        }
    }
}
