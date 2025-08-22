package com.vanky.im.message.service;

import com.vanky.im.common.model.UserSession;

public interface UserSessionService {

    /**
     * 从 Redis 获取并反序列化用户会话信息
     *
     * @param userId 用户ID
     * @return 用户会话信息，如果不存在或反序列化失败则返回 null
     */
    UserSession getUserSession(String userId);
}