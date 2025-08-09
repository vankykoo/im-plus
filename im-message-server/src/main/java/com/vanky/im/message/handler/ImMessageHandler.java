package com.vanky.im.message.handler;

import com.vanky.im.common.protocol.ChatMessage;

/**
 * 统一消息处理器接口
 * 提供统一的消息分发、异常处理和监控能力
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 统一消息处理入口，支持消息类型路由和处理器注册
 */
public interface ImMessageHandler {
    
    /**
     * 处理消息
     * 根据消息类型路由到对应的处理器
     * 
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID（可为null，如ACK消息）
     * @throws Exception 处理异常时抛出，由调用方决定重试策略
     */
    void handleMessage(ChatMessage chatMessage, String conversationId) throws Exception;
    
    /**
     * 注册消息处理器
     * 支持动态注册新的消息类型处理器
     * 
     * @param messageType 消息类型
     * @param processor 消息处理器
     */
    void registerProcessor(int messageType, MessageProcessor processor);
    
    /**
     * 获取处理统计信息
     * 
     * @return 处理统计信息
     */
    MessageHandlerStats getStats();
}
