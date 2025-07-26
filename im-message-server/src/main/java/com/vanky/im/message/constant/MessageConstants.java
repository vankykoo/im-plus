package com.vanky.im.message.constant;

/**
 * 消息处理相关常量
 */
public class MessageConstants {

    // ========== Redis Key 前缀 ==========
    
    /** 用户状态缓存前缀 */
    public static final String USER_STATUS_CACHE_PREFIX = "user:status:";
    
    /** 好友关系缓存前缀 */
    public static final String FRIENDSHIP_CACHE_PREFIX = "friendship:";
    
    /** 离线消息队列前缀 */
    public static final String OFFLINE_MSG_PREFIX = "user:offline_msg:";
    
    /** 未读数缓存前缀 */
    public static final String UNREAD_COUNT_PREFIX = "user:conversation:unread:";
    
    /** 会话序列号前缀 */
    public static final String CONVERSATION_SEQ_PREFIX = "conversation:seq:";
    
    /** 消息缓存前缀 */
    public static final String MESSAGE_CACHE_PREFIX = "msg:";
    
    /** 用户消息链缓存前缀 */
    public static final String USER_MSG_LIST_PREFIX = "user:msg:list:";
    
    /** 会话最新消息缓存前缀 */
    public static final String CONVERSATION_LATEST_MSG_PREFIX = "conversation:latest:";
    
    /** 用户会话列表缓存前缀 */
    public static final String USER_CONVERSATION_LIST_PREFIX = "user:conversation:list:";

    // ========== 缓存TTL配置 ==========
    
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

    // ========== 业务配置 ==========
    
    /** 用户消息链缓存最大保留条数 */
    public static final int MAX_USER_MSG_CACHE_SIZE = 1000;
    
    /** 离线消息队列最大长度 */
    public static final int MAX_OFFLINE_MSG_SIZE = 10000;
    
    /** 消息内容摘要最大长度 */
    public static final int MAX_MSG_CONTENT_SUMMARY_LENGTH = 50;

    // ========== 用户状态常量 ==========
    
    /** 用户状态：正常 */
    public static final int USER_STATUS_NORMAL = 1;
    
    /** 用户状态：封禁 */
    public static final int USER_STATUS_BANNED = 2;
    
    /** 用户状态：禁言 */
    public static final int USER_STATUS_MUTED = 3;

    // ========== 好友关系常量 ==========
    
    /** 关系类型：无关系 */
    public static final int RELATIONSHIP_TYPE_NONE = 0;
    
    /** 关系类型：好友 */
    public static final int RELATIONSHIP_TYPE_FRIENDS = 1;
    
    /** 关系类型：用户1拉黑用户2 */
    public static final int RELATIONSHIP_TYPE_USER1_BLOCKED_USER2 = 2;
    
    /** 关系类型：用户2拉黑用户1 */
    public static final int RELATIONSHIP_TYPE_USER2_BLOCKED_USER1 = 3;
    
    /** 关系类型：互相拉黑 */
    public static final int RELATIONSHIP_TYPE_MUTUAL_BLOCKED = 4;

    // ========== 会话类型常量 ==========
    
    /** 会话类型：私聊 */
    public static final int CONVERSATION_TYPE_PRIVATE = 1;
    
    /** 会话类型：群聊 */
    public static final int CONVERSATION_TYPE_GROUP = 2;

    // ========== 会话ID前缀 ==========
    
    /** 私聊会话ID前缀 */
    public static final String PRIVATE_CONVERSATION_PREFIX = "private_";
    
    /** 群聊会话ID前缀 */
    public static final String GROUP_CONVERSATION_PREFIX = "group_";

    // ========== 错误消息 ==========
    
    /** 用户被封禁错误消息 */
    public static final String ERROR_USER_BANNED = "用户被封禁";
    
    /** 用户被禁言错误消息 */
    public static final String ERROR_USER_MUTED = "用户被禁言";
    
    /** 被对方拉黑错误消息 */
    public static final String ERROR_BLOCKED_BY_USER = "被对方拉黑";
    
    /** 空消息占位符 */
    public static final String EMPTY_MESSAGE_PLACEHOLDER = "[空消息]";

    // ========== 私有构造函数 ==========
    
    private MessageConstants() {
        // 防止实例化
    }
}
