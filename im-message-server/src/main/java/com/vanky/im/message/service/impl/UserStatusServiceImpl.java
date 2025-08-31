package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.common.util.CacheSafetyManager;
import com.vanky.im.message.client.UserClient;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.service.UserStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户状态服务实现类
 * 通过缓存优先策略查询用户状态，缓存未命中时使用 Feign 客户端调用 im-user 服务
 * 体现统一的服务调用方式和 KISS 原则
 *
 * @updated 2025-08-14 - 重构为使用 Feign 客户端
 */
@Slf4j
@Service
public class UserStatusServiceImpl implements UserStatusService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserClient userClient;

    @Autowired
    private CacheSafetyManager cacheSafetyManager;

    @Override
    public UserStatusInfo getUserStatus(String userId) {
        String cacheKey = RedisKeyConstants.getUserStatusKey(userId);
        
        // 默认用户状态（当用户不存在时返回）
        UserStatusInfo defaultStatus = new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
        
        // 使用缓存安全管理器，自动处理缓存穿透和缓存击穿
        return cacheSafetyManager.safeGetFromCache(
            cacheKey,
            () -> fetchUserStatusFromService(userId), // 数据加载器
            RedisKeyConstants.USER_STATUS_CACHE_TTL_SECONDS,
            UserStatusInfo.class,
            defaultStatus // 默认值
        );
    }

    @Override
    public boolean isUserBanned(String userId) {
        UserStatusInfo statusInfo = getUserStatus(userId);
        return statusInfo.isBanned();
    }

    @Override
    public boolean isUserMuted(String userId) {
        UserStatusInfo statusInfo = getUserStatus(userId);
        return statusInfo.isMuted();
    }

    // 缓存操作已由CacheSafetyManager统一处理，移除重复代码（DRY原则）

    /**
     * 从im-user服务获取用户状态
     * 使用 Feign 客户端替换 RestTemplate，统一服务调用方式
     * 
     * 注意：此方法返回null表示用户不存在，由CacheSafetyManager自动缓存空值防止穿透
     */
    private UserStatusInfo fetchUserStatusFromService(String userId) {
        try {
            log.debug("通过Feign客户端调用im-user服务获取用户状态 - 用户ID: {}", userId);

            // 使用 Feign 客户端调用 im-user 服务，体现统一的服务调用方式
            ApiResponse<UserClient.UserStatusResponse> response = userClient.getUserStatus(userId);

            if (response != null && response.isSuccess() && response.getData() != null) {
                UserClient.UserStatusResponse statusData = response.getData();

                // 转换为内部的 UserStatusInfo 对象
                UserStatusInfo userStatusInfo = new UserStatusInfo(
                    statusData.getUserId(),
                    statusData.getStatus(),
                    statusData.getStatusDesc(),
                    statusData.getUpdateTime()
                );

                log.debug("成功获取用户状态 - 用户ID: {}, 状态: {}", userId, statusData.getStatus());
                return userStatusInfo;
            } else {
                log.warn("用户不存在或获取用户状态失败 - 用户ID: {}, 响应: {}", userId, response);
                // 返回null让CacheSafetyManager缓存空值，防止缓存穿透
                return null;
            }

        } catch (Exception e) {
            log.error("调用用户服务失败 - 用户ID: {}", userId, e);
            // 异常时返回null，由CacheSafetyManager使用默认值并缓存空值
            return null;
        }
    }
}
