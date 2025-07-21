package com.vanky.im.message.model;

import java.io.Serializable;

/**
 * 用户会话信息
 * 与im-gateway模块的UserSession保持一致
 */
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String host;
    private int port;
    private String nodeId; // 服务节点标识（gateway_instance_id）

    public UserSession() {
    }

    public UserSession(String userId, String host, int port) {
        this.userId = userId;
        this.host = host;
        this.port = port;
    }

    public UserSession(String userId, String host, int port, String nodeId) {
        this.userId = userId;
        this.host = host;
        this.port = port;
        this.nodeId = nodeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
} 