package com.vanky.im.message.client;

import com.vanky.im.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Feign 客户端降级处理
 * 提供统一的异常处理和降级策略，提高系统可靠性
 * 体现系统的容错能力和用户体验优化
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Slf4j
@Component
public class UserClientFallback implements UserClient {
    
    @Override
    public ApiResponse<String> getUsername(String userId) {
        log.warn("用户服务不可用，使用降级策略获取用户昵称 - 用户ID: {}", userId);
        return ApiResponse.success("用户" + userId); // 降级返回默认昵称
    }

    
    @Override
    public ApiResponse<UserStatusResponse> getUserStatus(String userId) {
        log.warn("用户服务不可用，使用降级策略获取用户状态 - 用户ID: {}", userId);
        // 降级返回正常状态
        UserStatusResponse defaultStatus = new UserStatusResponse(userId, 1, "正常", System.currentTimeMillis());
        return ApiResponse.success(defaultStatus);
    }
    
    @Override
    public ApiResponse<FriendshipResponse> getFriendship(String userId1, String userId2) {
        log.warn("用户服务不可用，使用降级策略获取好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);
        // 降级返回好友关系（保守策略，允许消息发送）
        FriendshipResponse defaultFriendship = new FriendshipResponse(
            userId1, userId2, 1, "好友", 
            System.currentTimeMillis(), System.currentTimeMillis()
        );
        return ApiResponse.success(defaultFriendship);
    }
    
    @Override
    public ApiResponse<Boolean> checkFriendship(String userId1, String userId2) {
        log.warn("用户服务不可用，使用降级策略检查好友关系 - 用户1: {}, 用户2: {}", userId1, userId2);
        // 降级返回true（保守策略，允许消息发送）
        return ApiResponse.success(true);
    }
}
