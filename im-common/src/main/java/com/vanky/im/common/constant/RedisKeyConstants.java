package com.vanky.im.common.constant;

/**
 * Redis Key 统一常量类
 * 集中管理项目中所有的Redis key前缀、TTL配置和业务配置
 * 
 * 更新记录 (2025-08-04 11:44:44 +08:00):
 * - 创建统一的Redis key常量管理类
 * - 合并MessageConstants、SessionConstants、客户端RedisConfig中的所有Redis key
 * - 按功能模块分组组织：会话、消息、用户状态、离线消息、客户端存储等
 * - 统一命名规范，保持现有格式兼容性
 * 
 * @author vanky
 * @since 2025-08-04
 */
public class RedisKeyConstants {

    // ========== 会话管理相关 Redis Key ==========

    /** 用户会话在Redis中的键前缀 */
    public static final String USER_SESSION_KEY_PREFIX = "im:session:user:";

    /** 在线用户集合的Redis键 */
    public static final String ONLINE_USERS_KEY = "im:online:users";
    
    // ========== 消息相关 Redis Key ==========
    
    /** 消息缓存前缀 */
    public static final String MESSAGE_CACHE_PREFIX = "msg:";

    /** 会话序列号前缀 */
    public static final String CONVERSATION_SEQ_PREFIX = "conversation:seq:";
    
    /** 用户级全局序列号前缀 */
    public static final String USER_GLOBAL_SEQ_PREFIX = "user:global:seq:";
    
    /** 用户消息链缓存前缀 */
    public static final String USER_MSG_LIST_PREFIX = "user:msg:list:";
    
    /** 会话最新消息缓存前缀 */
    public static final String CONVERSATION_LATEST_MSG_PREFIX = "conversation:latest:";
    
    /** 用户会话列表缓存前缀 */
    public static final String USER_CONVERSATION_LIST_PREFIX = "user:conversation:list:";
    
    /** 用户群聊同步点前缀 */
    public static final String USER_CONVERSATION_SEQ_PREFIX = "user:conversation:seq:";
    
    // ========== 用户状态相关 Redis Key ==========
    
    /** 用户状态缓存前缀 */
    public static final String USER_STATUS_CACHE_PREFIX = "user:status:";
    
    /** 好友关系缓存前缀 */
    public static final String FRIENDSHIP_CACHE_PREFIX = "friendship:";
    
    // ========== 离线消息相关 Redis Key ==========
    
    /** 离线消息队列前缀 */
    public static final String OFFLINE_MSG_PREFIX = "user:offline_msg:";
    
    /** 未读数缓存前缀 */
    public static final String UNREAD_COUNT_PREFIX = "user:conversation:unread:";
    
    // ========== 群组成员相关 Redis Key ==========
    
    /** 群组成员缓存前缀 */
    public static final String GROUP_MEMBERS_PREFIX = "group:members:";
    
    // ========== 客户端存储相关 Redis Key ==========
    
    /** 客户端同步序列号前缀 */
    public static final String CLIENT_SYNC_SEQ_PREFIX = "client:sync:seq:";
    
    /** 客户端群聊同步点前缀 */
    public static final String CLIENT_CONVERSATION_SEQ_PREFIX = "im:client:conversation:seq:";
    
    /** 客户端消息存储前缀 */
    public static final String CLIENT_MESSAGES_PREFIX = "im:client:messages:";
    
    /** 客户端统计信息前缀 */
    public static final String CLIENT_STATS_PREFIX = "im:client:stats:";
    
    // ========== TTL配置（秒） ==========
    
    /** 用户会话过期时间（30分钟） */
    public static final long SESSION_EXPIRE_TIME = 60 * 30;
    
    /** 用户状态缓存TTL（5分钟） */
    public static final long USER_STATUS_CACHE_TTL_SECONDS = 5 * 60;
    
    /** 好友关系缓存TTL（10分钟） */
    public static final long FRIENDSHIP_CACHE_TTL_SECONDS = 10 * 60;
    
    /** 消息缓存TTL（1天） */
    public static final long MESSAGE_CACHE_TTL_SECONDS = 24 * 60 * 60;
    
    /** 离线消息TTL（7天） */
    public static final long OFFLINE_MSG_TTL_SECONDS = 7 * 24 * 60 * 60;
    
    /** 会话相关缓存TTL（30天） */
    public static final long CONVERSATION_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60;
    
    /** 客户端数据过期时间（7天） */
    public static final long CLIENT_DATA_EXPIRE_SECONDS = 7 * 24 * 60 * 60;

    /** 群组成员缓存过期时间（24小时） */
    public static final int CACHE_EXPIRE_HOURS = 24;
    
    // ========== 业务配置 ==========
    
    /** 用户消息链缓存最大保留条数 */
    public static final int MAX_USER_MSG_CACHE_SIZE = 1000;
    
    /** 离线消息队列最大长度 */
    public static final int MAX_OFFLINE_MSG_SIZE = 10000;
    
    /** 消息内容摘要最大长度 */
    public static final int MAX_MSG_CONTENT_SUMMARY_LENGTH = 50;
    
    /** 客户端消息最大保留条数 */
    public static final int CLIENT_MESSAGES_MAX_COUNT = 1000;
    
    // ========== 工具方法 ==========
    
    /**
     * 获取用户会话的Redis键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserSessionKey(String userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }
    

    
    /**
     * 获取用户状态缓存键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserStatusKey(String userId) {
        return USER_STATUS_CACHE_PREFIX + userId;
    }
    
    /**
     * 获取好友关系缓存键
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return Redis键
     */
    public static String getFriendshipKey(String userId1, String userId2) {
        return FRIENDSHIP_CACHE_PREFIX + userId1 + ":" + userId2;
    }
    
    /**
     * 获取消息缓存键
     * @param msgId 消息ID
     * @return Redis键
     */
    public static String getMessageCacheKey(String msgId) {
        return MESSAGE_CACHE_PREFIX + msgId;
    }
    
    /**
     * 获取会话序列号键
     * @param conversationId 会话ID
     * @return Redis键
     */
    public static String getConversationSeqKey(String conversationId) {
        return CONVERSATION_SEQ_PREFIX + conversationId;
    }
    
    /**
     * 获取用户全局序列号键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserGlobalSeqKey(String userId) {
        return USER_GLOBAL_SEQ_PREFIX + userId;
    }
    
    /**
     * 获取用户消息链键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserMsgListKey(String userId) {
        return USER_MSG_LIST_PREFIX + userId;
    }
    
    /**
     * 获取离线消息队列键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getOfflineMsgKey(String userId) {
        return OFFLINE_MSG_PREFIX + userId;
    }
    
    /**
     * 获取未读数缓存键
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return Redis键
     */
    public static String getUnreadCountKey(String userId, String conversationId) {
        return UNREAD_COUNT_PREFIX + userId + ":" + conversationId;
    }
    
    /**
     * 获取群组成员缓存键
     * @param groupId 群组ID
     * @return Redis键
     */
    public static String getGroupMembersKey(String groupId) {
        return GROUP_MEMBERS_PREFIX + groupId;
    }
    
    /**
     * 获取用户群聊同步点键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserConversationSeqKey(String userId) {
        return USER_CONVERSATION_SEQ_PREFIX + userId;
    }
    
    /**
     * 获取客户端同步序列号键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getClientSyncSeqKey(String userId) {
        return CLIENT_SYNC_SEQ_PREFIX + userId;
    }
    
    /**
     * 获取客户端消息存储键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getClientMessagesKey(String userId) {
        return CLIENT_MESSAGES_PREFIX + userId;
    }
    
    /**
     * 获取客户端统计信息键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getClientStatsKey(String userId) {
        return CLIENT_STATS_PREFIX + userId;
    }
}
