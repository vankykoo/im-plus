package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.common.util.CacheSafetyManager;
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

    @Autowired
    private CacheSafetyManager cacheSafetyManager;

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
        String cacheKey = generateCacheKey(userId1, userId2);
        
        // 默认好友关系（无关系状态）
        FriendshipInfo defaultInfo = new FriendshipInfo(userId1, userId2, 0, System.currentTimeMillis(), System.currentTimeMillis());
        
        // 使用缓存安全管理器，自动处理缓存穿透和缓存击穿
        return cacheSafetyManager.safeGetFromCache(
            cacheKey,
            () -> fetchFriendshipInfoFromService(userId1, userId2), // 数据加载器
            RedisKeyConstants.FRIENDSHIP_CACHE_TTL_SECONDS,
            FriendshipInfo.class,
            defaultInfo // 默认值
        );
    }

    // 缓存操作已由CacheSafetyManager统一处理，移除重复代码（DRY原则）

    /**
     * 从im-user服务获取好友关系信息
     * 使用 Feign 客户端替换 RestTemplate，体现 KISS 原则
     * 
     * 注意：此方法返回null表示关系不存在，由CacheSafetyManager自动缓存空值防止穿透
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
                log.warn("好友关系不存在或获取失败 - 用户1: {}, 用户2: {}, 响应: {}", userId1, userId2, response);
                // 返回null让CacheSafetyManager缓存空值，防止缓存穿透
                return null;
            }

        } catch (Exception e) {
            log.error("调用用户服务失败 - 用户1: {}, 用户2: {}", userId1, userId2, e);
            // 异常时返回null，由CacheSafetyManager使用默认值并缓存空值
            return null;
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
