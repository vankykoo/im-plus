package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.service.OnlineStatusService;
import com.vanky.im.message.service.UserSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线状态服务实现类
 * 通过Redis查询用户的在线状态和会话信息
 */
@Slf4j
@Service
public class OnlineStatusServiceImpl implements OnlineStatusService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserSessionService userSessionService;

    @Override
    public UserSession getUserOnlineStatus(String userId) {
        return userSessionService.getUserSession(userId);
    }

    @Override
    public boolean isUserOnline(String userId) {
        try {
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            Boolean exists = redisTemplate.hasKey(sessionKey);
            boolean online = Boolean.TRUE.equals(exists);
            
            log.debug("检查用户在线状态 - 用户ID: {}, 在线状态: {}", userId, online);
            return online;
            
        } catch (Exception e) {
            log.error("检查用户在线状态失败 - 用户ID: {}", userId, e);
            return false;
        }
    }

    @Override
    public String getUserGatewayId(String userId) {
        UserSession userSession = getUserOnlineStatus(userId);
        if (userSession != null) {
            String gatewayId = userSession.getNodeId();
            log.debug("获取用户网关ID - 用户ID: {}, 网关ID: {}", userId, gatewayId);
            return gatewayId;
        }
        
        log.debug("用户离线，无网关ID - 用户ID: {}", userId);
        return null;
    }

    @Override
    public String getUserConnectionType(String userId) {
        UserSession userSession = getUserOnlineStatus(userId);
        if (userSession != null) {
            // UserSession 暂时没有连接类型字段，返回默认值
            String connectionType = "TCP"; // 默认连接类型
            log.debug("获取用户连接类型 - 用户ID: {}, 连接类型: {}", userId, connectionType);
            return connectionType;
        }

        log.debug("用户离线，无连接类型 - 用户ID: {}", userId);
        return null;
    }

    @Override
    public boolean isUserOnlineAtGateway(String userId, String gatewayId) {
        try {
            String userGatewayId = getUserGatewayId(userId);
            boolean atGateway = gatewayId != null && gatewayId.equals(userGatewayId);
            
            log.debug("检查用户是否在指定网关在线 - 用户ID: {}, 目标网关: {}, 实际网关: {}, 结果: {}", 
                    userId, gatewayId, userGatewayId, atGateway);
            return atGateway;
            
        } catch (Exception e) {
            log.error("检查用户网关在线状态失败 - 用户ID: {}, 网关ID: {}", userId, gatewayId, e);
            return false;
        }
    }
}
