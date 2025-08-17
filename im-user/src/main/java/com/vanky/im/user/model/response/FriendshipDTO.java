package com.vanky.im.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友关系数据传输对象
 * 遵循单一职责原则，专门负责好友关系信息的传输
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipDTO {
    
    /**
     * 用户1的ID
     */
    private String userId1;
    
    /**
     * 用户2的ID
     */
    private String userId2;
    
    /**
     * 关系类型：0-无关系，1-好友，2-userId1拉黑userId2，3-userId2拉黑userId1，4-互相拉黑
     */
    private Integer relationshipType;
    
    /**
     * 关系类型描述
     */
    private String relationshipDesc;
    
    /**
     * 关系建立时间戳
     */
    private Long createTime;
    
    /**
     * 关系更新时间戳
     */
    private Long updateTime;
    
    /**
     * 构造方法 - 基本关系信息
     */
    public FriendshipDTO(String userId1, String userId2, Integer relationshipType) {
        this.userId1 = userId1;
        this.userId2 = userId2;
        this.relationshipType = relationshipType;
        this.relationshipDesc = getRelationshipDescription(relationshipType);
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }
    
    /**
     * 判断是否为好友关系
     */
    public boolean isFriends() {
        return relationshipType != null && relationshipType == 1;
    }
    
    /**
     * 判断userId1是否被userId2拉黑
     */
    public boolean isUser1BlockedByUser2() {
        return relationshipType != null && (relationshipType == 3 || relationshipType == 4);
    }
    
    /**
     * 判断userId2是否被userId1拉黑
     */
    public boolean isUser2BlockedByUser1() {
        return relationshipType != null && (relationshipType == 2 || relationshipType == 4);
    }
    
    /**
     * 检查指定用户是否被对方拉黑
     */
    public boolean isBlocked(String fromUserId, String toUserId) {
        if (fromUserId.equals(userId1) && toUserId.equals(userId2)) {
            return isUser1BlockedByUser2();
        } else if (fromUserId.equals(userId2) && toUserId.equals(userId1)) {
            return isUser2BlockedByUser1();
        }
        return false;
    }
    
    /**
     * 获取关系类型描述
     */
    private String getRelationshipDescription(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 0: return "无关系";
            case 1: return "好友";
            case 2: return "用户1拉黑用户2";
            case 3: return "用户2拉黑用户1";
            case 4: return "互相拉黑";
            default: return "未知关系";
        }
    }
}
