package com.vanky.im.server.service;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.entity.GroupMessage;

/**
 * 消息缓存服务接口
 * @author vanky
 * @date 2025-06-08
 */
public interface MessageCacheService {

    /**
     * 缓存私聊消息
     * @param privateMessage 私聊消息实体
     * @param chatMessage 原始聊天消息
     * @param conversationId 会话ID
     */
    void cachePrivateMessage(PrivateMessage privateMessage, ChatMessage chatMessage, String conversationId);
    
    /**
     * 缓存群聊消息
     * @param groupMessage 群聊消息实体
     * @param chatMessage 原始聊天消息
     * @param conversationId 会话ID
     */
    void cacheGroupMessage(GroupMessage groupMessage, ChatMessage chatMessage, String conversationId);
    
    /**
     * 将消息添加到会话消息链缓存
     * @param conversationId 会话ID
     * @param msgId 消息ID
     * @param seq 序列号
     */
    void addMessageToRecentCache(String conversationId, Long msgId, Long seq);
} 