package com.vanky.im.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存安全管理器
 * 统一处理缓存穿透和缓存击穿问题
 * 
 * 设计原则：
 * - KISS: 使用简单的Redis命令实现分布式锁
 * - SRP: 专门负责缓存安全相关逻辑
 * - DRY: 避免在各个服务中重复实现缓存安全逻辑
 * 
 * @author vanky
 * @since 2025-01-17
 */
@Slf4j
@Component
public class CacheSafetyManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 空值标记，用于防止缓存穿透
    private static final String NULL_VALUE_MARKER = "NULL_CACHE_MARKER";
    
    // 分布式锁前缀
    private static final String LOCK_PREFIX = "cache:lock:";
    
    // 锁默认过期时间（秒）
    private static final int DEFAULT_LOCK_EXPIRE_SECONDS = 30;
    
    // 空值缓存默认TTL（秒）
    private static final int DEFAULT_NULL_VALUE_TTL_SECONDS = 300; // 5分钟
    
    // 锁获取重试参数
    private static final int LOCK_RETRY_TIMES = 3;
    private static final int LOCK_RETRY_INTERVAL_MS = 100;

    /**
     * 安全获取缓存数据，带缓存穿透和缓存击穿保护
     * 
     * @param <T> 返回数据类型
     * @param cacheKey 缓存键
     * @param dataLoader 数据加载函数（从数据库或其他数据源获取）
     * @param cacheTtlSeconds 缓存TTL（秒）
     * @param resultClass 结果类型
     * @return 数据对象，如果不存在则返回null
     */
    public <T> T safeGetFromCache(String cacheKey, Supplier<T> dataLoader, 
                                 long cacheTtlSeconds, Class<T> resultClass) {
        return safeGetFromCache(cacheKey, dataLoader, cacheTtlSeconds, resultClass, null);
    }

    /**
     * 安全获取缓存数据，带缓存穿透和缓存击穿保护
     * 
     * @param <T> 返回数据类型
     * @param cacheKey 缓存键
     * @param dataLoader 数据加载函数
     * @param cacheTtlSeconds 缓存TTL（秒）
     * @param resultClass 结果类型
     * @param defaultValue 默认值（当数据不存在时返回）
     * @return 数据对象
     */
    public <T> T safeGetFromCache(String cacheKey, Supplier<T> dataLoader, 
                                 long cacheTtlSeconds, Class<T> resultClass, T defaultValue) {
        try {
            // 1. 尝试从缓存获取数据
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            
            if (cached != null) {
                // 检查是否是空值标记
                if (NULL_VALUE_MARKER.equals(cached)) {
                    log.debug("缓存命中空值标记 - key: {}", cacheKey);
                    return defaultValue;
                }
                
                // 正常缓存数据
                if (resultClass.isInstance(cached)) {
                    log.debug("缓存命中 - key: {}", cacheKey);
                    return resultClass.cast(cached);
                }
            }
            
            // 2. 缓存未命中，使用分布式锁防止缓存击穿
            String lockKey = LOCK_PREFIX + cacheKey;
            String lockValue = String.valueOf(System.currentTimeMillis());
            
            boolean lockAcquired = acquireDistributedLock(lockKey, lockValue, DEFAULT_LOCK_EXPIRE_SECONDS);
            
            if (lockAcquired) {
                try {
                    // 获取锁成功，双重检查缓存（可能在等待锁期间其他线程已经加载了数据）
                    cached = redisTemplate.opsForValue().get(cacheKey);
                    if (cached != null) {
                        if (NULL_VALUE_MARKER.equals(cached)) {
                            return defaultValue;
                        }
                        if (resultClass.isInstance(cached)) {
                            return resultClass.cast(cached);
                        }
                    }
                    
                    // 从数据源加载数据
                    log.debug("从数据源加载数据 - key: {}", cacheKey);
                    T data = dataLoader.get();
                    
                    if (data != null) {
                        // 缓存正常数据
                        redisTemplate.opsForValue().set(cacheKey, data, cacheTtlSeconds, TimeUnit.SECONDS);
                        log.debug("缓存数据成功 - key: {}", cacheKey);
                        return data;
                    } else {
                        // 缓存空值标记，防止缓存穿透
                        redisTemplate.opsForValue().set(cacheKey, NULL_VALUE_MARKER, 
                                                      DEFAULT_NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                        log.debug("缓存空值标记成功 - key: {}", cacheKey);
                        return defaultValue;
                    }
                    
                } finally {
                    // 释放分布式锁
                    releaseDistributedLock(lockKey, lockValue);
                }
            } else {
                // 获取锁失败，等待一段时间后重试获取缓存
                log.debug("获取分布式锁失败，等待后重试 - key: {}", cacheKey);
                
                for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
                    try {
                        Thread.sleep(LOCK_RETRY_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    cached = redisTemplate.opsForValue().get(cacheKey);
                    if (cached != null) {
                        if (NULL_VALUE_MARKER.equals(cached)) {
                            return defaultValue;
                        }
                        if (resultClass.isInstance(cached)) {
                            return resultClass.cast(cached);
                        }
                    }
                }
                
                // 重试后仍无法获取缓存，直接调用数据加载器（降级处理）
                log.warn("重试获取缓存失败，直接调用数据加载器 - key: {}", cacheKey);
                T data = dataLoader.get();
                return data != null ? data : defaultValue;
            }
            
        } catch (Exception e) {
            log.error("缓存安全获取失败 - key: {}", cacheKey, e);
            // 异常降级，直接调用数据加载器
            try {
                T data = dataLoader.get();
                return data != null ? data : defaultValue;
            } catch (Exception ex) {
                log.error("数据加载器调用失败 - key: {}", cacheKey, ex);
                return defaultValue;
            }
        }
    }

    /**
     * 获取分布式锁
     * 
     * @param lockKey 锁键
     * @param lockValue 锁值（用于释放时验证）
     * @param expireSeconds 过期时间（秒）
     * @return 是否获取成功
     */
    private boolean acquireDistributedLock(String lockKey, String lockValue, int expireSeconds) {
        try {
            // 使用 SET NX EX 命令原子性地设置锁
            Boolean result = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofSeconds(expireSeconds));
            
            boolean acquired = Boolean.TRUE.equals(result);
            if (acquired) {
                log.debug("获取分布式锁成功 - lockKey: {}", lockKey);
            } else {
                log.debug("获取分布式锁失败 - lockKey: {}", lockKey);
            }
            
            return acquired;
        } catch (Exception e) {
            log.error("获取分布式锁异常 - lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     * 
     * @param lockKey 锁键
     * @param lockValue 锁值（用于验证是否是自己的锁）
     */
    private void releaseDistributedLock(String lockKey, String lockValue) {
        try {
            // 使用Lua脚本确保只释放自己的锁
            String luaScript = 
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('DEL', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                return connection.eval(luaScript.getBytes(), 
                                     org.springframework.data.redis.connection.ReturnType.INTEGER, 
                                     1, lockKey.getBytes(), lockValue.getBytes());
            });
            
            log.debug("释放分布式锁成功 - lockKey: {}", lockKey);
        } catch (Exception e) {
            log.error("释放分布式锁异常 - lockKey: {}", lockKey, e);
        }
    }

    /**
     * 手动设置空值缓存
     * 
     * @param cacheKey 缓存键
     */
    public void setNullValueCache(String cacheKey) {
        setNullValueCache(cacheKey, DEFAULT_NULL_VALUE_TTL_SECONDS);
    }

    /**
     * 手动设置空值缓存
     * 
     * @param cacheKey 缓存键
     * @param ttlSeconds TTL（秒）
     */
    public void setNullValueCache(String cacheKey, int ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(cacheKey, NULL_VALUE_MARKER, ttlSeconds, TimeUnit.SECONDS);
            log.debug("设置空值缓存成功 - key: {}, ttl: {}s", cacheKey, ttlSeconds);
        } catch (Exception e) {
            log.error("设置空值缓存失败 - key: {}", cacheKey, e);
        }
    }

    /**
     * 删除缓存
     * 
     * @param cacheKey 缓存键
     */
    public void deleteCache(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
            log.debug("删除缓存成功 - key: {}", cacheKey);
        } catch (Exception e) {
            log.error("删除缓存失败 - key: {}", cacheKey, e);
        }
    }
}
