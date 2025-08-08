package com.vanky.im.gateway.conversation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话处理器配置类
 * 
 * @author vanky
 * @create 2025/8/7
 * @description 管理会话级串行化处理的相关配置参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "conversation.processor")
public class ConversationProcessorConfig {
    
    /**
     * 是否启用会话级串行化处理
     * 默认禁用，需要显式配置启用
     */
    private boolean enabled = false;
    
    /**
     * 工作线程池大小
     * 建议设置为CPU核心数的1-2倍
     */
    private int workerPoolSize = 16;
    
    /**
     * 每个会话队列的最大容量
     * 防止内存溢出，超过容量时会拒绝新消息
     */
    private int queueCapacity = 1000;
    
    /**
     * 队列空闲超时时间（毫秒）
     * 队列在此时间内无消息时会被回收
     */
    private long idleTimeoutMs = 300000L; // 5分钟
    
    /**
     * 工作线程名称前缀
     */
    private String workerThreadPrefix = "conversation-worker-";
    
    /**
     * 监控统计间隔（毫秒）
     */
    private long monitorIntervalMs = 30000L; // 30秒
    
    /**
     * 最大并发会话数
     * 超过此数量时会拒绝新会话
     */
    private int maxConversations = 10000;
    
    /**
     * 消息处理超时时间（毫秒）
     * 超过此时间的消息会被记录警告
     */
    private long messageTimeoutMs = 5000L; // 5秒
    
    /**
     * 是否启用详细日志
     * 开启后会记录更多调试信息，但会影响性能
     */
    private boolean verboseLogging = false;
    
    /**
     * 队列满时的处理策略
     * REJECT: 拒绝新消息
     * BLOCK: 阻塞等待
     * DROP_OLDEST: 丢弃最老的消息
     */
    private QueueFullPolicy queueFullPolicy = QueueFullPolicy.REJECT;
    
    /**
     * 优雅关闭超时时间（毫秒）
     * 系统关闭时等待消息处理完成的最大时间
     */
    private long shutdownTimeoutMs = 30000L; // 30秒
    
    /**
     * 队列满时的处理策略枚举
     */
    public enum QueueFullPolicy {
        REJECT,     // 拒绝新消息
        BLOCK,      // 阻塞等待
        DROP_OLDEST // 丢弃最老的消息
    }
    
    /**
     * 验证配置参数的有效性
     * 
     * @throws IllegalArgumentException 如果配置参数无效
     */
    public void validate() {
        if (workerPoolSize <= 0) {
            throw new IllegalArgumentException("workerPoolSize must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (idleTimeoutMs <= 0) {
            throw new IllegalArgumentException("idleTimeoutMs must be positive");
        }
        if (maxConversations <= 0) {
            throw new IllegalArgumentException("maxConversations must be positive");
        }
        if (messageTimeoutMs <= 0) {
            throw new IllegalArgumentException("messageTimeoutMs must be positive");
        }
        if (shutdownTimeoutMs <= 0) {
            throw new IllegalArgumentException("shutdownTimeoutMs must be positive");
        }
    }
    
    /**
     * 获取配置摘要信息
     * 
     * @return 配置摘要字符串
     */
    public String getSummary() {
        return String.format(
            "ConversationProcessor[enabled=%s, workerPoolSize=%d, queueCapacity=%d, " +
            "maxConversations=%d, idleTimeout=%dms, messageTimeout=%dms]",
            enabled, workerPoolSize, queueCapacity, maxConversations, 
            idleTimeoutMs, messageTimeoutMs
        );
    }
}
