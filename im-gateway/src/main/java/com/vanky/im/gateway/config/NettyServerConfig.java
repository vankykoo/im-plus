package com.vanky.im.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author vanky
 * @create 2025/6/5
 * @description Netty服务器配置类，从application.yml中读取配置
 */
@Configuration
@ConfigurationProperties(prefix = "netty.server")
public class NettyServerConfig {

    private ServerProperties tcp = new ServerProperties();
    private ServerProperties udp = new ServerProperties();
    private WebSocketServerProperties websocket = new WebSocketServerProperties();

    public ServerProperties getTcp() {
        return tcp;
    }

    public void setTcp(ServerProperties tcp) {
        this.tcp = tcp;
    }

    public ServerProperties getUdp() {
        return udp;
    }

    public void setUdp(ServerProperties udp) {
        this.udp = udp;
    }

    public WebSocketServerProperties getWebsocket() {
        return websocket;
    }

    public void setWebsocket(WebSocketServerProperties websocket) {
        this.websocket = websocket;
    }

    /**
     * 通用服务器属性
     */
    public static class ServerProperties {
        private boolean enabled = true;
        private int port;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /**
     * WebSocket 服务器特有属性
     */
    public static class WebSocketServerProperties extends ServerProperties {
        private String path = "/websocket";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}