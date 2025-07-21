package com.vanky.im.message.constant;

/**
 * 会话常量类，定义Redis键名和过期时间
 * 与im-gateway模块的SessionConstants保持一致
 */
public class SessionConstants {

    /**
     * 用户会话在Redis中的键前缀
     */
    public static final String USER_SESSION_KEY_PREFIX = "im:session:user:";
    
    /**
     * Channel映射在Redis中的键前缀
     */
    public static final String CHANNEL_USER_KEY_PREFIX = "im:session:channel:";
    
    /**
     * 在线用户集合的Redis键
     */
    public static final String ONLINE_USERS_KEY = "im:online:users";
    
    /**
     * 用户会话过期时间（秒）
     */
    public static final long SESSION_EXPIRE_TIME = 60 * 30; // 30分钟

    /**
     * 获取用户会话的Redis键
     * @param userId 用户ID
     * @return Redis键
     */
    public static String getUserSessionKey(String userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }
} 