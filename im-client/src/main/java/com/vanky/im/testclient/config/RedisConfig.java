package com.vanky.im.testclient.config;

import com.vanky.im.common.constant.RedisKeyConstants;

/**
 * Redis配置管理器
 * 负责管理Redis相关配置
 * 完全移除本地文件读写操作，使用硬编码配置与服务端保持一致
 *
 * @author vanky
 * @create 2025/7/30
 * @update 2025/8/4 - 移除Redis key常量定义，统一使用RedisKeyConstants类
 */
public class RedisConfig {

    // Redis服务器配置（与服务端保持一致）
    private static final String REDIS_HOST = "192.168.101.43";
    private static final int REDIS_PORT = 6379;
    private static final int REDIS_DATABASE = 0;
    private static final String REDIS_PASSWORD = "123456";
    private static final int REDIS_TIMEOUT = 3000;

    // 连接池配置
    private static final int POOL_MAX_TOTAL = 10;
    private static final int POOL_MAX_IDLE = 5;
    private static final int POOL_MIN_IDLE = 1;

    // 注意：Redis Key前缀和TTL配置已迁移到RedisKeyConstants类

    static {
        System.out.println("Redis配置初始化完成 - 使用硬编码配置，已移除本地文件I/O操作");
        System.out.println("Redis服务器: " + REDIS_HOST + ":" + REDIS_PORT +
                          ", 数据库: " + REDIS_DATABASE + ", 密码: 已配置");
    }
    
    /**
     * 获取Redis主机
     */
    public static String getHost() {
        // 支持环境变量覆盖，如果没有则使用硬编码值
        return System.getenv("REDIS_HOST") != null ?
               System.getenv("REDIS_HOST") : REDIS_HOST;
    }

    /**
     * 获取Redis端口
     */
    public static int getPort() {
        String envPort = System.getenv("REDIS_PORT");
        return envPort != null ? Integer.parseInt(envPort) : REDIS_PORT;
    }

    /**
     * 获取Redis数据库
     */
    public static int getDatabase() {
        String envDatabase = System.getenv("REDIS_DATABASE");
        return envDatabase != null ? Integer.parseInt(envDatabase) : REDIS_DATABASE;
    }

    /**
     * 获取Redis密码
     */
    public static String getPassword() {
        return System.getenv("REDIS_PASSWORD") != null ?
               System.getenv("REDIS_PASSWORD") : REDIS_PASSWORD;
    }

    /**
     * 获取连接超时时间
     */
    public static int getTimeout() {
        String envTimeout = System.getenv("REDIS_TIMEOUT");
        return envTimeout != null ? Integer.parseInt(envTimeout) : REDIS_TIMEOUT;
    }

    /**
     * 获取连接池最大连接数
     */
    public static int getMaxTotal() {
        String envMaxTotal = System.getenv("REDIS_POOL_MAX_TOTAL");
        return envMaxTotal != null ? Integer.parseInt(envMaxTotal) : POOL_MAX_TOTAL;
    }

    /**
     * 获取连接池最大空闲连接数
     */
    public static int getMaxIdle() {
        String envMaxIdle = System.getenv("REDIS_POOL_MAX_IDLE");
        return envMaxIdle != null ? Integer.parseInt(envMaxIdle) : POOL_MAX_IDLE;
    }

    /**
     * 获取连接池最小空闲连接数
     */
    public static int getMinIdle() {
        String envMinIdle = System.getenv("REDIS_POOL_MIN_IDLE");
        return envMinIdle != null ? Integer.parseInt(envMinIdle) : POOL_MIN_IDLE;
    }
    
    /**
     * 获取客户端用户级序列号过期时间（秒）
     */
    public static long getClientUserSeqExpire() {
        String envExpire = System.getenv("REDIS_CLIENT_USER_SEQ_EXPIRE");
        return envExpire != null ? Long.parseLong(envExpire) : RedisKeyConstants.CLIENT_USER_SEQ_TTL_SECONDS;
    }

    /**
     * 获取客户端会话级序列号过期时间（秒）
     */
    public static long getClientConversationSeqExpire() {
        String envExpire = System.getenv("REDIS_CLIENT_CONVERSATION_SEQ_EXPIRE");
        return envExpire != null ? Long.parseLong(envExpire) : RedisKeyConstants.CLIENT_CONVERSATION_SEQ_TTL_SECONDS;
    }

    /**
     * 获取客户端用户级序列号Key前缀
     */
    public static String getSyncSeqPrefix() {
        return System.getenv("REDIS_SYNC_SEQ_PREFIX") != null ?
               System.getenv("REDIS_SYNC_SEQ_PREFIX") : RedisKeyConstants.CLIENT_SYNC_SEQ_PREFIX;
    }

    /**
     * 获取客户端会话级序列号Key前缀
     */
    public static String getConversationSeqPrefix() {
        return System.getenv("REDIS_CONVERSATION_SEQ_PREFIX") != null ?
               System.getenv("REDIS_CONVERSATION_SEQ_PREFIX") : RedisKeyConstants.CLIENT_CONVERSATION_SEQ_PREFIX;
    }
}
