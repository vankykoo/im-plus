package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.ApiResponse;
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

    @Override
    public UserStatusInfo getUserStatus(String userId) {
        try {
            // 1. 先从缓存查询
            UserStatusInfo cachedStatus = getCachedUserStatus(userId);
            if (cachedStatus != null) {
                log.debug("从缓存获取用户状态 - 用户ID: {}, 状态: {}", userId, cachedStatus.getStatus());
                return cachedStatus;
            }

            // 2. 缓存未命中，调用im-user服务
            UserStatusInfo userStatus = fetchUserStatusFromService(userId);
            
            // 3. 将结果缓存
            cacheUserStatus(userId, userStatus);
            
            log.debug("从服务获取用户状态 - 用户ID: {}, 状态: {}", userId, userStatus.getStatus());
            return userStatus;
            
        } catch (Exception e) {
            log.error("获取用户状态失败 - 用户ID: {}", userId, e);
            // 异常时返回正常状态，避免影响消息发送
            return new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
        }
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

    /**
     * 从缓存获取用户状态
     */
    private UserStatusInfo getCachedUserStatus(String userId) {
        try {
            String cacheKey = RedisKeyConstants.getUserStatusKey(userId);
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? (UserStatusInfo) cached : null;
        } catch (Exception e) {
            log.warn("获取用户状态缓存失败 - 用户ID: {}", userId, e);
            return null;
        }
    }

    /**
     * 缓存用户状态
     */
    private void cacheUserStatus(String userId, UserStatusInfo statusInfo) {
        try {
            String cacheKey = RedisKeyConstants.getUserStatusKey(userId);
            redisTemplate.opsForValue().set(cacheKey, statusInfo, RedisKeyConstants.USER_STATUS_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("缓存用户状态成功 - 用户ID: {}", userId);
        } catch (Exception e) {
            log.warn("缓存用户状态失败 - 用户ID: {}", userId, e);
        }
    }

    /**
     * 从im-user服务获取用户状态
     * 使用 Feign 客户端替换 RestTemplate，统一服务调用方式
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
                log.warn("获取用户状态失败 - 用户ID: {}, 响应: {}", userId, response);
                // 服务调用失败时返回正常状态
                return new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
            }

        } catch (Exception e) {
            log.error("调用用户服务失败 - 用户ID: {}", userId, e);
            // 异常降级处理，返回正常状态
            return new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
        }
    }
}
