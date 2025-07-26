package com.vanky.im.message.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vanky.im.message.constant.MessageConstants;

/**
 * 用户状态服务接口
 * 负责查询用户的状态信息，包括封禁、禁言等状态
 */
public interface UserStatusService {

    /**
     * 获取用户状态信息
     * @param userId 用户ID
     * @return 用户状态信息
     */
    UserStatusInfo getUserStatus(String userId);

    /**
     * 检查用户是否被封禁
     * @param userId 用户ID
     * @return true-被封禁，false-正常
     */
    boolean isUserBanned(String userId);

    /**
     * 检查用户是否被禁言
     * @param userId 用户ID
     * @return true-被禁言，false-正常
     */
    boolean isUserMuted(String userId);

    /**
     * 用户状态信息
     */
    class UserStatusInfo {
        private String userId;
        private int status; // 1-正常，2-封禁，3-禁言
        private String reason; // 状态原因
        private long expireTime; // 过期时间（毫秒时间戳，0表示永久）

        public UserStatusInfo() {}

        public UserStatusInfo(String userId, int status, String reason, long expireTime) {
            this.userId = userId;
            this.status = status;
            this.reason = reason;
            this.expireTime = expireTime;
        }

        // Getters and Setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        /**
         * 检查状态是否已过期
         * @return true-已过期，false-未过期
         */
        @JsonIgnore
        public boolean isExpired() {
            return expireTime > 0 && System.currentTimeMillis() > expireTime;
        }

        /**
         * 检查用户是否正常
         * @return true-正常，false-异常
         */
        @JsonIgnore
        public boolean isNormal() {
            return status == MessageConstants.USER_STATUS_NORMAL || isExpired();
        }

        /**
         * 检查用户是否被封禁
         * @return true-被封禁，false-正常
         */
        @JsonIgnore
        public boolean isBanned() {
            return status == MessageConstants.USER_STATUS_BANNED && !isExpired();
        }

        /**
         * 检查用户是否被禁言
         * @return true-被禁言，false-正常
         */
        @JsonIgnore
        public boolean isMuted() {
            return status == MessageConstants.USER_STATUS_MUTED && !isExpired();
        }
    }
}
