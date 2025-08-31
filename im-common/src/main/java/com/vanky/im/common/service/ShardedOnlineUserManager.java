package com.vanky.im.common.service;

import com.vanky.im.common.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 分片在线用户管理器
 * 解决Redis热KEY问题，将在线用户集合分片存储
 * 
 * 设计原则：
 * - KISS: 使用简单的哈希取模分片策略
 * - SOLID-S: 单一职责，只负责在线用户的分片管理
 * - DRY: 抽象通用的分片操作，避免重复代码
 * 
 * @author vanky
 * @since 2025-08-31
 */
@Slf4j
@Component
public class ShardedOnlineUserManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分片数量，设计为16个分片
     * 原则：DRY - 使用统一的常量定义
     */
    private static final int SHARD_COUNT = RedisKeyConstants.ONLINE_USERS_SHARD_COUNT;

    /**
     * 分片掩码，用于快速计算分片索引
     */
    private static final int SHARD_MASK = SHARD_COUNT - 1;

    /**
     * 在线用户分片键前缀
     * 原则：DRY - 使用统一的常量定义
     */
    private static final String SHARD_KEY_PREFIX = RedisKeyConstants.ONLINE_USERS_SHARD_PREFIX;

    /**
     * 添加用户到在线集合
     * 原则：SOLID-S - 单一职责，只负责添加操作
     * 
     * @param userId 用户ID
     */
    public void addOnlineUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("用户ID为空，无法添加到在线集合");
            return;
        }

        try {
            String shardKey = getShardKey(userId);
            redisTemplate.opsForSet().add(shardKey, userId);
            
            // 设置合理的过期时间，防止内存泄漏（原则：YAGNI - 只实现必要的功能）
            redisTemplate.expire(shardKey, RedisKeyConstants.SESSION_EXPIRE_TIME * 2, TimeUnit.SECONDS);
            
            log.debug("添加用户到在线集合成功 - 用户ID: {}, 分片: {}", userId, shardKey);
            
        } catch (Exception e) {
            log.error("添加用户到在线集合失败 - 用户ID: {}", userId, e);
        }
    }

    /**
     * 从在线集合中移除用户
     * 原则：SOLID-S - 单一职责，只负责移除操作
     * 
     * @param userId 用户ID
     * @return 是否成功移除
     */
    public boolean removeOnlineUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("用户ID为空，无法从在线集合移除");
            return false;
        }

        try {
            String shardKey = getShardKey(userId);
            Long removedCount = redisTemplate.opsForSet().remove(shardKey, userId);
            
            boolean success = removedCount != null && removedCount > 0;
            log.debug("从在线集合移除用户 - 用户ID: {}, 分片: {}, 移除数量: {}", 
                     userId, shardKey, removedCount);
            
            return success;
            
        } catch (Exception e) {
            log.error("从在线集合移除用户失败 - 用户ID: {}", userId, e);
            return false;
        }
    }

    /**
     * 检查用户是否在线
     * 原则：KISS - 简单直接的查询操作
     * 
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        try {
            String shardKey = getShardKey(userId);
            Boolean isMember = redisTemplate.opsForSet().isMember(shardKey, userId);
            
            boolean online = Boolean.TRUE.equals(isMember);
            log.debug("检查用户在线状态 - 用户ID: {}, 分片: {}, 在线: {}", userId, shardKey, online);
            
            return online;
            
        } catch (Exception e) {
            log.error("检查用户在线状态失败 - 用户ID: {}", userId, e);
            return false;
        }
    }

    /**
     * 获取所有在线用户（分片聚合）
     * 原则：YAGNI - 保留原有功能，但优化实现
     * 注意：此方法用于兼容性，大规模场景下应避免调用
     * 
     * @return 所有在线用户集合
     */
    public Set<String> getAllOnlineUsers() {
        Set<String> allUsers = new HashSet<>();
        
        try {
            // 遍历所有分片，聚合结果
            for (int i = 0; i < SHARD_COUNT; i++) {
                String shardKey = SHARD_KEY_PREFIX + i;
                Set<Object> shardUsers = redisTemplate.opsForSet().members(shardKey);
                
                if (shardUsers != null && !shardUsers.isEmpty()) {
                    for (Object user : shardUsers) {
                        if (user instanceof String) {
                            allUsers.add((String) user);
                        }
                    }
                }
            }
            
            log.debug("获取所有在线用户完成 - 总数: {}", allUsers.size());
            
        } catch (Exception e) {
            log.error("获取所有在线用户失败", e);
        }
        
        return allUsers;
    }

    /**
     * 获取在线用户总数（高效统计）
     * 原则：性能优化 - 避免获取完整用户列表
     * 
     * @return 在线用户总数
     */
    public long getOnlineUserCount() {
        long totalCount = 0;
        
        try {
            for (int i = 0; i < SHARD_COUNT; i++) {
                String shardKey = SHARD_KEY_PREFIX + i;
                Long shardCount = redisTemplate.opsForSet().size(shardKey);
                if (shardCount != null) {
                    totalCount += shardCount;
                }
            }
            
            log.debug("获取在线用户总数 - 总数: {}", totalCount);
            
        } catch (Exception e) {
            log.error("获取在线用户总数失败", e);
        }
        
        return totalCount;
    }

    /**
     * 清理指定分片中的过期用户
     * 原则：SOLID-S - 分片级别的清理操作
     * 
     * @param shardIndex 分片索引
     * @param sessionChecker 会话检查器
     * @return 清理的用户数量
     */
    public int cleanExpiredUsersInShard(int shardIndex, SessionChecker sessionChecker) {
        if (shardIndex < 0 || shardIndex >= SHARD_COUNT) {
            log.warn("无效的分片索引: {}", shardIndex);
            return 0;
        }

        int cleanedCount = 0;
        String shardKey = SHARD_KEY_PREFIX + shardIndex;
        
        try {
            Set<Object> users = redisTemplate.opsForSet().members(shardKey);
            if (users == null || users.isEmpty()) {
                return 0;
            }

            for (Object userObj : users) {
                if (userObj instanceof String) {
                    String userId = (String) userObj;
                    
                    // 检查会话是否存在
                    if (!sessionChecker.hasValidSession(userId)) {
                        redisTemplate.opsForSet().remove(shardKey, userId);
                        cleanedCount++;
                        log.debug("清理过期在线用户 - 用户ID: {}, 分片: {}", userId, shardIndex);
                    }
                }
            }
            
            log.debug("清理分片{}过期用户完成 - 清理数量: {}", shardIndex, cleanedCount);
            
        } catch (Exception e) {
            log.error("清理分片{}过期用户失败", shardIndex, e);
        }
        
        return cleanedCount;
    }

    /**
     * 计算用户的分片索引
     * 原则：KISS - 简单的哈希取模算法
     * 
     * @param userId 用户ID
     * @return 分片索引
     */
    private int getShardIndex(String userId) {
        // 使用字符串哈希值的绝对值进行分片计算
        return Math.abs(userId.hashCode()) & SHARD_MASK;
    }

    /**
     * 获取用户的分片键
     * 原则：DRY - 统一的键生成逻辑
     * 
     * @param userId 用户ID
     * @return 分片键
     */
    private String getShardKey(String userId) {
        int shardIndex = getShardIndex(userId);
        return SHARD_KEY_PREFIX + shardIndex;
    }

    /**
     * 会话检查器接口
     * 原则：SOLID-D - 依赖抽象而非具体实现
     */
    @FunctionalInterface
    public interface SessionChecker {
        /**
         * 检查用户是否有有效会话
         * 
         * @param userId 用户ID
         * @return 是否有有效会话
         */
        boolean hasValidSession(String userId);
    }
}
