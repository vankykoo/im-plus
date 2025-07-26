package com.vanky.im.message.service;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 好友关系服务接口
 * 负责查询用户之间的好友关系和黑名单状态
 */
public interface FriendshipService {

    /**
     * 检查两个用户是否为好友关系
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return true-是好友，false-不是好友
     */
    boolean areFriends(String userId1, String userId2);

    /**
     * 检查用户是否被对方拉黑
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     * @return true-被拉黑，false-未被拉黑
     */
    boolean isBlocked(String fromUserId, String toUserId);

    /**
     * 获取好友关系详细信息
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 好友关系信息
     */
    FriendshipInfo getFriendshipInfo(String userId1, String userId2);

    /**
     * 好友关系信息
     */
    class FriendshipInfo {
        private String userId1;
        private String userId2;
        private int relationshipType; // 0-无关系，1-好友，2-userId1拉黑userId2，3-userId2拉黑userId1，4-互相拉黑
        private long createTime; // 关系建立时间
        private long updateTime; // 关系更新时间

        public FriendshipInfo() {}

        public FriendshipInfo(String userId1, String userId2, int relationshipType, long createTime, long updateTime) {
            this.userId1 = userId1;
            this.userId2 = userId2;
            this.relationshipType = relationshipType;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }

        // Getters and Setters
        public String getUserId1() {
            return userId1;
        }

        public void setUserId1(String userId1) {
            this.userId1 = userId1;
        }

        public String getUserId2() {
            return userId2;
        }

        public void setUserId2(String userId2) {
            this.userId2 = userId2;
        }

        public int getRelationshipType() {
            return relationshipType;
        }

        public void setRelationshipType(int relationshipType) {
            this.relationshipType = relationshipType;
        }

        public long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public long getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(long updateTime) {
            this.updateTime = updateTime;
        }

        /**
         * 检查是否为好友关系
         * @return true-是好友，false-不是好友
         */
        @JsonIgnore
        public boolean isFriends() {
            return relationshipType == 1;
        }

        /**
         * 检查userId1是否被userId2拉黑
         * @return true-被拉黑，false-未被拉黑
         */
        @JsonIgnore
        public boolean isUser1BlockedByUser2() {
            return relationshipType == 3 || relationshipType == 4;
        }

        /**
         * 检查userId2是否被userId1拉黑
         * @return true-被拉黑，false-未被拉黑
         */
        @JsonIgnore
        public boolean isUser2BlockedByUser1() {
            return relationshipType == 2 || relationshipType == 4;
        }

        /**
         * 检查指定用户是否被对方拉黑
         * @param fromUserId 发送方用户ID
         * @param toUserId 接收方用户ID
         * @return true-被拉黑，false-未被拉黑
         */
        @JsonIgnore
        public boolean isBlocked(String fromUserId, String toUserId) {
            if (fromUserId.equals(userId1) && toUserId.equals(userId2)) {
                return isUser1BlockedByUser2();
            } else if (fromUserId.equals(userId2) && toUserId.equals(userId1)) {
                return isUser2BlockedByUser1();
            }
            return false;
        }
    }
}
