package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.message.client.UserClient;
import com.vanky.im.message.service.FriendshipService;
import com.vanky.im.message.service.FriendshipService.FriendshipInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 好友关系服务实现类
 * 通过缓存优先策略查询好友关系，缓存未命中时使用 Feign 客户端调用 im-user 服务
 * 体现 KISS 原则，简化 HTTP 调用复杂性
 *
 * @updated 2025-08-14 - 重构为使用 Feign 客户端
 */
@Slf4j
@Service
public class FriendshipServiceImpl implements FriendshipService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserClient userClient;

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
     * 使用 Feign 客户端替换 RestTemplate，体现 KISS 原则
     */
    private FriendshipInfo fetchFriendshipInfoFromService(String userId1, String userId2) {
        try {
            log.debug("通过Feign客户端调用im-user服务获取好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);

            // 使用 Feign 客户端调用 im-user 服务，简化 HTTP 调用
            ApiResponse<UserClient.FriendshipResponse> response = userClient.getFriendship(userId1, userId2);

            if (response != null && response.isSuccess() && response.getData() != null) {
                UserClient.FriendshipResponse friendshipData = response.getData();

                // 转换为内部的 FriendshipInfo 对象
                FriendshipInfo friendshipInfo = new FriendshipInfo(
                    friendshipData.getUserId1(),
                    friendshipData.getUserId2(),
                    friendshipData.getRelationshipType(),
                    friendshipData.getCreateTime(),
                    friendshipData.getUpdateTime()
                );

                log.debug("成功获取好友关系 - 用户1: {}, 用户2: {}, 关系类型: {}",
                        userId1, userId2, friendshipData.getRelationshipType());
                return friendshipInfo;
            } else {
                log.warn("获取好友关系失败 - 用户1: {}, 用户2: {}, 响应: {}", userId1, userId2, response);
                // 服务调用失败时返回无关系状态
                return new FriendshipInfo(userId1, userId2, 0, System.currentTimeMillis(), System.currentTimeMillis());
            }

        } catch (Exception e) {
            log.error("调用用户服务失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            // 异常降级处理，返回无关系状态
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
