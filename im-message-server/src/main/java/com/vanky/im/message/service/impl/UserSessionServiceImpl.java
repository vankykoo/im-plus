package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.service.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserSessionServiceImpl implements UserSessionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public UserSession getUserSession(String userId) {
        String sessionKey = SessionConstants.getUserSessionKey(userId);
        try {
            Object sessionObject = redisTemplate.opsForValue().get(sessionKey);
            if (sessionObject instanceof UserSession) {
                return (UserSession) sessionObject;
            } else if (sessionObject != null) {
                log.warn("Redis中获取的对象类型不是UserSession - userId: {}, type: {}", userId, sessionObject.getClass().getName());
            }
        } catch (Exception e) {
            log.error("从Redis获取UserSession失败 - userId: {}", userId, e);
        }
        return null;
    }
}