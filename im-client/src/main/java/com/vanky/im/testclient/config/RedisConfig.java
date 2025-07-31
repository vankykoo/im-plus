package com.vanky.im.testclient.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Redis配置管理器
 * 负责加载和管理Redis相关配置
 * 
 * @author vanky
 * @create 2025/7/30
 */
public class RedisConfig {
    
    private static final String CONFIG_FILE = "redis-client.properties";
    private static final Properties properties = new Properties();
    
    static {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try (InputStream inputStream = RedisConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                System.out.println("Redis配置加载成功");
            } else {
                System.err.println("Redis配置文件未找到: " + CONFIG_FILE);
                // 使用默认配置
                setDefaultConfig();
            }
        } catch (IOException e) {
            System.err.println("加载Redis配置失败: " + e.getMessage());
            // 使用默认配置
            setDefaultConfig();
        }
    }
    
    /**
     * 设置默认配置
     */
    private static void setDefaultConfig() {
        properties.setProperty("redis.host", "localhost");
        properties.setProperty("redis.port", "6379");
        properties.setProperty("redis.database", "2");
        properties.setProperty("redis.timeout", "3000");
        properties.setProperty("redis.pool.maxTotal", "10");
        properties.setProperty("redis.pool.maxIdle", "5");
        properties.setProperty("redis.pool.minIdle", "1");
        properties.setProperty("redis.sync.seq.expire", "604800");
        properties.setProperty("redis.messages.expire", "604800");
        properties.setProperty("redis.messages.max.count", "1000");
        System.out.println("使用默认Redis配置");
    }
    
    /**
     * 获取Redis主机
     */
    public static String getHost() {
        return properties.getProperty("redis.host", "localhost");
    }
    
    /**
     * 获取Redis端口
     */
    public static int getPort() {
        return Integer.parseInt(properties.getProperty("redis.port", "6379"));
    }
    
    /**
     * 获取Redis数据库
     */
    public static int getDatabase() {
        return Integer.parseInt(properties.getProperty("redis.database", "2"));
    }
    
    /**
     * 获取连接超时时间
     */
    public static int getTimeout() {
        return Integer.parseInt(properties.getProperty("redis.timeout", "3000"));
    }
    
    /**
     * 获取连接池最大连接数
     */
    public static int getMaxTotal() {
        return Integer.parseInt(properties.getProperty("redis.pool.maxTotal", "10"));
    }
    
    /**
     * 获取连接池最大空闲连接数
     */
    public static int getMaxIdle() {
        return Integer.parseInt(properties.getProperty("redis.pool.maxIdle", "5"));
    }
    
    /**
     * 获取连接池最小空闲连接数
     */
    public static int getMinIdle() {
        return Integer.parseInt(properties.getProperty("redis.pool.minIdle", "1"));
    }
    
    /**
     * 获取同步序列号过期时间（秒）
     */
    public static int getSyncSeqExpire() {
        return Integer.parseInt(properties.getProperty("redis.sync.seq.expire", "604800"));
    }
    
    /**
     * 获取消息过期时间（秒）
     */
    public static int getMessagesExpire() {
        return Integer.parseInt(properties.getProperty("redis.messages.expire", "604800"));
    }
    
    /**
     * 获取消息最大存储数量
     */
    public static int getMaxMessageCount() {
        return Integer.parseInt(properties.getProperty("redis.messages.max.count", "1000"));
    }
    
    /**
     * 获取同步序列号Key前缀
     */
    public static String getSyncSeqPrefix() {
        return properties.getProperty("redis.key.sync.seq.prefix", "im:client:sync:seq:");
    }
    
    /**
     * 获取消息Key前缀
     */
    public static String getMessagesPrefix() {
        return properties.getProperty("redis.key.messages.prefix", "im:client:messages:");
    }
    
    /**
     * 获取统计Key前缀
     */
    public static String getStatsPrefix() {
        return properties.getProperty("redis.key.stats.prefix", "im:client:stats:");
    }
}
