package com.vanky.im.user.service;

import com.vanky.im.user.model.response.FriendshipDTO;

/**
 * 好友关系服务接口
 * 遵循接口隔离原则，专门负责好友关系相关的业务逻辑
 * 
 * @author vanky
 * @since 2025-08-14
 */
public interface FriendshipService {
    
    /**
     * 查询两个用户之间的好友关系
     * 
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 好友关系DTO，如果无关系则返回关系类型为0的DTO
     */
    FriendshipDTO getFriendshipInfo(String userId1, String userId2);
    
    /**
     * 检查两个用户是否为好友关系
     * 
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return true-是好友，false-不是好友
     */
    boolean areFriends(String userId1, String userId2);
    
    /**
     * 检查用户是否被对方拉黑
     * 
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     * @return true-被拉黑，false-未被拉黑
     */
    boolean isBlocked(String fromUserId, String toUserId);
}
