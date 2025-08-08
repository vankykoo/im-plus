package com.vanky.im.gateway.conversation;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.Channel;

/**
 * 会话消息包装类
 * 用于在会话处理队列中传递消息和相关上下文信息
 * 
 * @author vanky
 * @create 2025/8/7
 * @description 包装ChatMessage和Channel，便于在会话队列中传递完整的处理上下文
 */
public class ConversationMessage {
    
    /**
     * 原始聊天消息
     */
    private final ChatMessage chatMessage;
    
    /**
     * 发送方的网络连接通道
     */
    private final Channel channel;
    
    /**
     * 会话ID（用于路由和队列管理）
     */
    private final String conversationId;
    
    /**
     * 消息创建时间戳
     */
    private final long timestamp;
    
    /**
     * 重试次数（用于异常处理）
     */
    private final int retryCount;
    
    /**
     * 构造函数
     * 
     * @param chatMessage 原始聊天消息
     * @param channel 发送方的网络连接通道
     * @param conversationId 会话ID
     */
    public ConversationMessage(ChatMessage chatMessage, Channel channel, String conversationId) {
        this(chatMessage, channel, conversationId, System.currentTimeMillis(), 0);
    }
    
    /**
     * 完整构造函数
     * 
     * @param chatMessage 原始聊天消息
     * @param channel 发送方的网络连接通道
     * @param conversationId 会话ID
     * @param timestamp 消息创建时间戳
     * @param retryCount 重试次数
     */
    public ConversationMessage(ChatMessage chatMessage, Channel channel, String conversationId, 
                             long timestamp, int retryCount) {
        this.chatMessage = chatMessage;
        this.channel = channel;
        this.conversationId = conversationId;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
    }
    
    /**
     * 创建重试消息
     * 
     * @return 重试次数+1的新消息对象
     */
    public ConversationMessage createRetryMessage() {
        return new ConversationMessage(chatMessage, channel, conversationId, timestamp, retryCount + 1);
    }
    
    /**
     * 获取消息年龄（毫秒）
     * 
     * @return 从创建到现在的时间差
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * 检查Channel是否仍然活跃
     * 
     * @return true如果Channel活跃，false否则
     */
    public boolean isChannelActive() {
        return channel != null && channel.isActive();
    }
    
    // Getter方法
    
    public ChatMessage getChatMessage() {
        return chatMessage;
    }
    
    public Channel getChannel() {
        return channel;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    @Override
    public String toString() {
        return "ConversationMessage{" +
                "conversationId='" + conversationId + '\'' +
                ", messageType=" + (chatMessage != null ? chatMessage.getType() : "null") +
                ", fromId=" + (chatMessage != null ? chatMessage.getFromId() : "null") +
                ", toId=" + (chatMessage != null ? chatMessage.getToId() : "null") +
                ", timestamp=" + timestamp +
                ", retryCount=" + retryCount +
                ", channelActive=" + isChannelActive() +
                '}';
    }
}
