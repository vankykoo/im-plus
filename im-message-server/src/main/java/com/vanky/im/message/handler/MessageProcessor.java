package com.vanky.im.message.handler;

import com.vanky.im.common.protocol.ChatMessage;

/**
 * 消息处理器接口
 * 定义统一的消息处理规范
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 消息处理器统一接口，支持不同类型消息的处理
 */
public interface MessageProcessor {
    
    /**
     * 处理消息
     * 
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID（可为null）
     * @throws Exception 处理异常时抛出
     */
    void process(ChatMessage chatMessage, String conversationId) throws Exception;
    
    /**
     * 获取支持的消息类型
     * 
     * @return 消息类型数组
     */
    int[] getSupportedMessageTypes();
    
    /**
     * 获取处理器名称
     * 
     * @return 处理器名称
     */
    String getProcessorName();
}
