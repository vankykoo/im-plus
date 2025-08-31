package com.vanky.im.gateway.service;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.service.ShardedOnlineUserManager;
import com.vanky.im.gateway.session.UserChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户下线处理服务
 * 统一处理用户下线时的内存清理和Redis清理逻辑
 * 确保无论是正常登出、异常断开还是心跳超时，都能正确清理用户状态
 */
@Slf4j
@Service
public class UserOfflineService {

    @Autowired
    private UserChannelManager userChannelManager;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ShardedOnlineUserManager shardedOnlineUserManager;

    /**
     * 处理用户下线
     * 包含内存清理和Redis清理，确保状态一致性
     * 
     * @param userId 下线用户ID
     * @param reason 下线原因（用于日志记录）
     */
    public void handleUserOffline(String userId, String reason) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("用户下线处理失败 - 用户ID为空, 原因: {}", reason);
            return;
        }

        log.info("开始处理用户下线 - 用户ID: {}, 原因: {}", userId, reason);

        try {
            // 1. 检查用户是否真的在线（避免重复处理）
            boolean wasOnline = userChannelManager.isUserOnline(userId);
            
            // 2. 清理内存中的连接映射
            userChannelManager.unbindChannel(userId);
            
            // 3. 清理Redis中的用户状态
            cleanupRedisUserState(userId);
            
            if (wasOnline) {
                log.info("用户下线处理完成 - 用户ID: {}, 原因: {}, 当前在线用户数: {}", 
                        userId, reason, userChannelManager.getOnlineUserCount());
            } else {
                log.debug("用户已离线，跳过重复处理 - 用户ID: {}, 原因: {}", userId, reason);
            }
            
        } catch (Exception e) {
            log.error("用户下线处理异常 - 用户ID: {}, 原因: {}", userId, reason, e);
        }
    }

    /**
     * 清理Redis中的用户状态
     * 包括用户会话和在线用户集合
     * 
     * @param userId 用户ID
     */
    private void cleanupRedisUserState(String userId) {
        try {
            // 1. 删除用户会话
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            Boolean sessionDeleted = redisTemplate.delete(sessionKey);
            
            // 2. 从在线用户集合中移除（使用分片管理器）
            boolean removed = shardedOnlineUserManager.removeOnlineUser(userId);
            
            log.debug("Redis状态清理完成 - 用户ID: {}, 会话删除: {}, 在线集合移除: {}", 
                    userId, sessionDeleted, removed);
            
        } catch (Exception e) {
            log.error("Redis状态清理失败 - 用户ID: {}", userId, e);
        }
    }

    /**
     * 批量处理用户下线（用于清理任务）
     * 
     * @param userIds 用户ID列表
     * @param reason 下线原因
     */
    public void handleBatchUserOffline(java.util.List<String> userIds, String reason) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        
        log.info("开始批量处理用户下线 - 用户数量: {}, 原因: {}", userIds.size(), reason);
        
        for (String userId : userIds) {
            handleUserOffline(userId, reason);
        }
        
        log.info("批量用户下线处理完成 - 用户数量: {}, 原因: {}", userIds.size(), reason);
    }

    /**
     * 检查并清理过期的在线用户
     * 定期调用此方法可以清理Redis中的僵尸在线状态
     * 使用分片清理策略，提升性能和减少热KEY问题
     */
    public void cleanupExpiredOnlineUsers() {
        try {
            log.debug("开始分片清理过期的在线用户状态");
            
            int totalExpiredCount = 0;
            
            // 创建会话检查器（SOLID-D原则：依赖抽象）
            ShardedOnlineUserManager.SessionChecker sessionChecker = userId -> {
                String sessionKey = SessionConstants.getUserSessionKey(userId);
                return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey));
            };
            
            // 分片清理，避免一次性获取所有在线用户造成的性能问题
            for (int shardIndex = 0; shardIndex < RedisKeyConstants.ONLINE_USERS_SHARD_COUNT; shardIndex++) {
                int expiredCount = shardedOnlineUserManager.cleanExpiredUsersInShard(shardIndex, sessionChecker);
                totalExpiredCount += expiredCount;
            }
            
            if (totalExpiredCount > 0) {
                long remainingUsers = shardedOnlineUserManager.getOnlineUserCount();
                log.info("分片清理过期在线用户完成 - 清理数量: {}, 剩余在线用户: {}", 
                        totalExpiredCount, remainingUsers);
            } else {
                log.debug("没有发现过期的在线用户");
            }
            
        } catch (Exception e) {
            log.error("分片清理过期在线用户失败", e);
        }
    }
}
