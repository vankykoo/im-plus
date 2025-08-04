package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.message.service.FriendshipService;
import com.vanky.im.message.service.FriendshipService.FriendshipInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 好友关系服务实现类
 * 通过缓存优先策略查询好友关系，缓存未命中时调用im-user服务
 */
@Slf4j
@Service
public class FriendshipServiceImpl implements FriendshipService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    // im-user服务地址（可配置化）
    private static final String USER_SERVICE_URL = "http://localhost:8081";

    @Override
    public boolean areFriends(String userId1, String userId2) {
        FriendshipInfo friendshipInfo = getFriendshipInfo(userId1, userId2);
        return friendshipInfo.isFriends();
    }

    @Override
    public boolean isBlocked(String fromUserId, String toUserId) {
        FriendshipInfo friendshipInfo = getFriendshipInfo(fromUserId, toUserId);
        return friendshipInfo.isBlocked(fromUserId, toUserId);
    }

    @Override
    public FriendshipInfo getFriendshipInfo(String userId1, String userId2) {
        try {
            // 1. 先从缓存查询
            FriendshipInfo cachedInfo = getCachedFriendshipInfo(userId1, userId2);
            if (cachedInfo != null) {
                log.debug("从缓存获取好友关系 - 用户1: {}, 用户2: {}, 关系类型: {}", 
                        userId1, userId2, cachedInfo.getRelationshipType());
                return cachedInfo;
            }

            // 2. 缓存未命中，调用im-user服务
            FriendshipInfo friendshipInfo = fetchFriendshipInfoFromService(userId1, userId2);
            
            // 3. 将结果缓存
            cacheFriendshipInfo(userId1, userId2, friendshipInfo);
            
            log.debug("从服务获取好友关系 - 用户1: {}, 用户2: {}, 关系类型: {}", 
                    userId1, userId2, friendshipInfo.getRelationshipType());
            return friendshipInfo;
            
        } catch (Exception e) {
            log.error("获取好友关系失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            // 异常时返回无关系状态，允许消息发送但可能需要后续处理
            return new FriendshipInfo(userId1, userId2, 0, System.currentTimeMillis(), System.currentTimeMillis());
        }
    }

    /**
     * 从缓存获取好友关系信息
     */
    private FriendshipInfo getCachedFriendshipInfo(String userId1, String userId2) {
        try {
            String cacheKey = generateCacheKey(userId1, userId2);
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? (FriendshipInfo) cached : null;
        } catch (Exception e) {
            log.warn("获取好友关系缓存失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            return null;
        }
    }

    /**
     * 缓存好友关系信息
     */
    private void cacheFriendshipInfo(String userId1, String userId2, FriendshipInfo friendshipInfo) {
        try {
            String cacheKey = generateCacheKey(userId1, userId2);
            redisTemplate.opsForValue().set(cacheKey, friendshipInfo, RedisKeyConstants.FRIENDSHIP_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("缓存好友关系成功 - 用户1: {}, 用户2: {}", userId1, userId2);
        } catch (Exception e) {
            log.warn("缓存好友关系失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
        }
    }

    /**
     * 从im-user服务获取好友关系信息
     */
    private FriendshipInfo fetchFriendshipInfoFromService(String userId1, String userId2) {
        try {
            // TODO: 实际调用im-user服务的API
            // String url = USER_SERVICE_URL + "/api/friendship/" + userId1 + "/" + userId2;
            // FriendshipResponse response = restTemplate.getForObject(url, FriendshipResponse.class);
            
            // 暂时模拟返回好友关系
            log.debug("调用im-user服务获取好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);
            return new FriendshipInfo(userId1, userId2, 1, System.currentTimeMillis(), System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("调用im-user服务失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            // 服务调用失败时返回无关系状态
            return new FriendshipInfo(userId1, userId2, 0, System.currentTimeMillis(), System.currentTimeMillis());
        }
    }

    /**
     * 生成缓存key，确保用户ID顺序一致
     */
    private String generateCacheKey(String userId1, String userId2) {
        // 按字典序排序，确保缓存key的一致性
        if (userId1.compareTo(userId2) <= 0) {
            return RedisKeyConstants.getFriendshipKey(userId1, userId2);
        } else {
            return RedisKeyConstants.getFriendshipKey(userId2, userId1);
        }
    }
}
