package com.vanky.im.message.service;

import com.vanky.im.common.model.UserSession;

/**
 * 在线状态服务接口
 * 负责查询用户的在线状态和网关信息
 */
public interface OnlineStatusService {

    /**
     * 获取用户在线状态和会话信息
     * @param userId 用户ID
     * @return 用户会话信息，如果用户离线则返回null
     */
    UserSession getUserOnlineStatus(String userId);

    /**
     * 检查用户是否在线
     * @param userId 用户ID
     * @return true-在线，false-离线
     */
    boolean isUserOnline(String userId);

    /**
     * 获取用户所在的网关ID
     * @param userId 用户ID
     * @return 网关ID，如果用户离线则返回null
     */
    String getUserGatewayId(String userId);

    /**
     * 获取用户的连接类型
     * @param userId 用户ID
     * @return 连接类型（TCP、UDP、WebSocket等），如果用户离线则返回null
     */
    String getUserConnectionType(String userId);

    /**
     * 检查用户是否在指定网关在线
     * @param userId 用户ID
     * @param gatewayId 网关ID
     * @return true-在指定网关在线，false-不在或离线
     */
    boolean isUserOnlineAtGateway(String userId, String gatewayId);
}
