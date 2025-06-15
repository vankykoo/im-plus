package com.vanky.im.gateway.session;

import io.netty.channel.Channel;

import java.io.Serializable;

/**
 * @author vanky
 * @create 2025/5/22 21:43
 * @description 用户会话信息
 */
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String host;
    private int port;
    private String nodeId; // 服务节点标识
    // Channel不能序列化，因此不会存储在Redis中
    private transient Channel channel;

    public UserSession() {
    }

    public UserSession(String userId, String host, int port, Channel channel) {
        this.userId = userId;
        this.host = host;
        this.port = port;
        this.channel = channel;
    }

    public UserSession(String userId, String host, int port, String nodeId, Channel channel) {
        this.userId = userId;
        this.host = host;
        this.port = port;
        this.nodeId = nodeId;
        this.channel = channel;
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

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
