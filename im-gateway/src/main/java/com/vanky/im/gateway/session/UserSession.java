package com.vanky.im.gateway.session;

import io.netty.channel.Channel;

/**
 * @author vanky
 * @create 2025/5/22 21:43
 * @description 用户会话信息
 */
public class UserSession {

    private String userId;
    private String host;
    private int port;
    private Channel channel;

    public UserSession() {
    }

    public UserSession(String userId, String host, int port, Channel channel) {
        this.userId = userId;
        this.host = host;
        this.port = port;
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
}
