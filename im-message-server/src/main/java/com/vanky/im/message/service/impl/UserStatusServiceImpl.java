package com.vanky.im.message.service.impl;

import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.service.UserStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 用户状态服务实现类
 * 通过缓存优先策略查询用户状态，缓存未命中时调用im-user服务
 */
@Slf4j
@Service
public class UserStatusServiceImpl implements UserStatusService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    // 使用常量类中的配置
    // im-user服务地址（可配置化）
    private static final String USER_SERVICE_URL = "http://localhost:8081";

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
            String cacheKey = MessageConstants.USER_STATUS_CACHE_PREFIX + userId;
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
            String cacheKey = MessageConstants.USER_STATUS_CACHE_PREFIX + userId;
            redisTemplate.opsForValue().set(cacheKey, statusInfo, MessageConstants.USER_STATUS_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("缓存用户状态成功 - 用户ID: {}", userId);
        } catch (Exception e) {
            log.warn("缓存用户状态失败 - 用户ID: {}", userId, e);
        }
    }

    /**
     * 从im-user服务获取用户状态
     */
    private UserStatusInfo fetchUserStatusFromService(String userId) {
        try {
            // TODO: 实际调用im-user服务的API
            // String url = USER_SERVICE_URL + "/api/users/" + userId + "/status";
            // UserStatusResponse response = restTemplate.getForObject(url, UserStatusResponse.class);
            
            // 暂时模拟返回正常状态
            log.debug("调用im-user服务获取用户状态 - 用户ID: {}", userId);
            return new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
            
        } catch (Exception e) {
            log.error("调用im-user服务失败 - 用户ID: {}", userId, e);
            // 服务调用失败时返回正常状态
            return new UserStatusInfo(userId, MessageConstants.USER_STATUS_NORMAL, "正常", 0);
        }
    }
}
