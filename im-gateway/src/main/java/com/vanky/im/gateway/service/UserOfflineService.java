package com.vanky.im.gateway.service;

import com.vanky.im.common.constant.SessionConstants;
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
            
            // 2. 从在线用户集合中移除
            Long removedCount = redisTemplate.opsForSet().remove(SessionConstants.ONLINE_USERS_KEY, userId);
            
            log.debug("Redis状态清理完成 - 用户ID: {}, 会话删除: {}, 在线集合移除: {}", 
                    userId, sessionDeleted, removedCount);
            
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
     */
    public void cleanupExpiredOnlineUsers() {
        try {
            log.debug("开始清理过期的在线用户状态");
            
            // 获取所有在线用户
            java.util.Set<Object> onlineUsers = redisTemplate.opsForSet().members(SessionConstants.ONLINE_USERS_KEY);
            if (onlineUsers == null || onlineUsers.isEmpty()) {
                log.debug("没有在线用户需要检查");
                return;
            }
            
            int expiredCount = 0;
            for (Object userObj : onlineUsers) {
                String userId = userObj.toString();
                
                // 检查用户会话是否存在
                String sessionKey = SessionConstants.getUserSessionKey(userId);
                Boolean sessionExists = redisTemplate.hasKey(sessionKey);
                
                if (!Boolean.TRUE.equals(sessionExists)) {
                    // 会话不存在，从在线用户集合中移除
                    redisTemplate.opsForSet().remove(SessionConstants.ONLINE_USERS_KEY, userId);
                    expiredCount++;
                    log.debug("清理过期在线用户 - 用户ID: {}", userId);
                }
            }
            
            if (expiredCount > 0) {
                log.info("清理过期在线用户完成 - 清理数量: {}, 剩余在线用户: {}", 
                        expiredCount, onlineUsers.size() - expiredCount);
            } else {
                log.debug("没有发现过期的在线用户");
            }
            
        } catch (Exception e) {
            log.error("清理过期在线用户失败", e);
        }
    }
}
