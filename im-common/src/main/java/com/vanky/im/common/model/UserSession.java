package com.vanky.im.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.channel.Channel;

import java.io.Serializable;

/**
 * 用户会话信息
 * 统一的用户会话模型，供所有模块使用
 * 
 * @author vanky
 * @create 2025/5/22 21:43
 */
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String host;
    private int port;
    private String nodeId; // 服务节点标识（gateway_instance_id）
    private Integer clientType;
    private Integer version;
    
    // Channel不能序列化，因此不会存储在Redis中
    // 只在gateway模块中使用，其他模块忽略此字段
    @JsonIgnore
    private transient Channel channel;

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

    // Getters and Setters
    
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

    @JsonIgnore
    public Channel getChannel() {
        return channel;
    }

    @JsonIgnore
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Integer getClientType() {
        return clientType;
    }

    public void setClientType(Integer clientType) {
        this.clientType = clientType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "userId='" + userId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", nodeId='" + nodeId + '\'' +
                ", hasChannel=" + (channel != null) +
                '}';
    }
}
