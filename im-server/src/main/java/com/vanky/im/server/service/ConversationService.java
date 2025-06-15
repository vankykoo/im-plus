package com.vanky.im.server.service;

/**
 * 会话服务接口
 * @author vanky
 * @date 2025-06-08
 */
public interface ConversationService {

    /**
     * 更新会话最后消息时间
     * @param conversationId 会话ID
     * @param timestamp 消息时间戳
     * @return 是否更新成功
     */
    boolean updateLastMsgTime(String conversationId, long timestamp);
} 