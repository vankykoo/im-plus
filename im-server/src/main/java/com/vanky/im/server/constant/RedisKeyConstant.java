package com.vanky.im.server.constant;

/**
 * Redis键常量类
 * @author vanky
 * @date 2025-06-08
 */
public interface RedisKeyConstant {

    /**
     * 私聊消息缓存前缀
     */
    String PRIVATE_MSG_PREFIX = "im:msg:private:";
    
    /**
     * 群聊消息缓存前缀
     */
    String GROUP_MSG_PREFIX = "im:msg:group:";
    
    /**
     * 会话信息缓存前缀
     */
    String CONVERSATION_INFO_PREFIX = "im:conv:info:";
    
    /**
     * 会话最近消息缓存前缀（ZSET结构）
     */
    String CONVERSATION_RECENT_MSG_PREFIX = "im:conv:recent:";
    
    /**
     * 消息缓存过期时间（7天）
     */
    long MSG_CACHE_EXPIRE_TIME = 7 * 24 * 60 * 60;
    
    /**
     * 会话信息缓存过期时间（1天）
     */
    long CONVERSATION_INFO_EXPIRE_TIME = 24 * 60 * 60;
    
    /**
     * 会话最近消息缓存过期时间（7天）
     */
    long CONVERSATION_RECENT_MSG_EXPIRE_TIME = 7 * 24 * 60 * 60;
    
    /**
     * 最近消息缓存数量限制
     */
    int RECENT_MESSAGE_LIMIT = 50;
} 