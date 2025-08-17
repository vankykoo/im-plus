package com.vanky.im.message.client;

import com.vanky.im.common.model.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 客户端
 * 遵循依赖倒置原则，定义对 im-user 服务的声明式调用接口
 * 体现 KISS 原则，简化 HTTP 调用复杂性
 * 
 * @author vanky
 * @since 2025-08-14
 */
@FeignClient(
    name = "im-user",  // 使用Nacos服务名进行调用
    path = "/users",
    fallback = UserClientFallback.class
)
public interface UserClient {
    
    /**
     * 根据用户ID获取用户昵称
     * 为消息服务提供简化的用户名查询接口
     * 
     * @param userId 用户ID
     * @return 用户昵称响应
     */
    @GetMapping("/username/{userId}")
    ApiResponse<String> getUsername(@PathVariable("userId") String userId);

    
    /**
     * 根据用户ID获取用户状态
     * 为消息服务提供用户状态查询接口
     * 
     * @param userId 用户ID
     * @return 用户状态响应
     */
    @GetMapping("/status/{userId}")
    ApiResponse<UserStatusResponse> getUserStatus(@PathVariable("userId") String userId);
    
    /**
     * 查询两个用户之间的好友关系
     * 为消息服务提供好友关系查询接口
     * 
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 好友关系响应
     */
    @GetMapping("/friendship")
    ApiResponse<FriendshipResponse> getFriendship(
            @RequestParam("userId1") String userId1,
            @RequestParam("userId2") String userId2);
    
    /**
     * 检查两个用户是否为好友
     * 为消息服务提供简化的好友关系检查接口
     * 
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 是否为好友响应
     */
    @GetMapping("/friendship/check")
    ApiResponse<Boolean> checkFriendship(
            @RequestParam("userId1") String userId1,
            @RequestParam("userId2") String userId2);
    
    /**
     * 用户状态响应对象
     * 遵循单一职责原则，专门用于接收用户状态数据
     */
    class UserStatusResponse {
        private String userId;
        private Integer status;
        private String statusDesc;
        private Long updateTime;
        
        // 构造方法
        public UserStatusResponse() {}
        
        public UserStatusResponse(String userId, Integer status, String statusDesc, Long updateTime) {
            this.userId = userId;
            this.status = status;
            this.statusDesc = statusDesc;
            this.updateTime = updateTime;
        }
        
        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        
        public String getStatusDesc() { return statusDesc; }
        public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }
        
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
        
        /**
         * 判断用户是否为正常状态
         */
        public boolean isNormal() {
            return status != null && status == 1;
        }
    }
    
    /**
     * 好友关系响应对象
     * 遵循单一职责原则，专门用于接收好友关系数据
     */
    class FriendshipResponse {
        private String userId1;
        private String userId2;
        private Integer relationshipType;
        private String relationshipDesc;
        private Long createTime;
        private Long updateTime;
        
        // 构造方法
        public FriendshipResponse() {}
        
        public FriendshipResponse(String userId1, String userId2, Integer relationshipType, 
                                String relationshipDesc, Long createTime, Long updateTime) {
            this.userId1 = userId1;
            this.userId2 = userId2;
            this.relationshipType = relationshipType;
            this.relationshipDesc = relationshipDesc;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
        
        // Getters and Setters
        public String getUserId1() { return userId1; }
        public void setUserId1(String userId1) { this.userId1 = userId1; }
        
        public String getUserId2() { return userId2; }
        public void setUserId2(String userId2) { this.userId2 = userId2; }
        
        public Integer getRelationshipType() { return relationshipType; }
        public void setRelationshipType(Integer relationshipType) { this.relationshipType = relationshipType; }
        
        public String getRelationshipDesc() { return relationshipDesc; }
        public void setRelationshipDesc(String relationshipDesc) { this.relationshipDesc = relationshipDesc; }
        
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
        
        /**
         * 判断是否为好友关系
         */
        public boolean isFriends() {
            return relationshipType != null && relationshipType == 1;
        }
        
        /**
         * 检查指定用户是否被对方拉黑
         */
        public boolean isBlocked(String fromUserId, String toUserId) {
            if (fromUserId.equals(userId1) && toUserId.equals(userId2)) {
                return relationshipType != null && (relationshipType == 3 || relationshipType == 4);
            } else if (fromUserId.equals(userId2) && toUserId.equals(userId1)) {
                return relationshipType != null && (relationshipType == 2 || relationshipType == 4);
            }
            return false;
        }
    }
}
