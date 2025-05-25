package com.vanky.im.gateway;

import com.vanky.im.gateway.netty.NettyServerTCP;
import com.vanky.im.gateway.netty.NettyServerUDP;
import com.vanky.im.gateway.netty.NettyServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.vanky.im.common.constant.PortConstant.DEFAULT_TCP_PORT;
import static com.vanky.im.common.constant.PortConstant.DEFAULT_UDP_PORT;
import static com.vanky.im.common.constant.PortConstant.DEFAULT_WEBSOCKET_PORT;
import static com.vanky.im.common.constant.UriConstant.DEFAULT_WEBSOCKET_PATH;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description 网关启动类
 */
public class GatewayApplication {

    private static final Logger logger = LoggerFactory.getLogger(GatewayApplication.class);

    public static void main(String[] args) {
        // 启动TCP服务器
        new Thread(() -> {
            try {
                NettyServerTCP tcpServer = new NettyServerTCP();
                tcpServer.init();
                // 监听TCP 8080端口
                tcpServer.start(DEFAULT_TCP_PORT);
            } catch (InterruptedException e) {
                logger.error("TCP server startup failed.", e);
                Thread.currentThread().interrupt();
            }
        }, "TCP-Server-Thread").start();

        // 启动UDP服务器
        new Thread(() -> {
            try {
                NettyServerUDP udpServer = new NettyServerUDP();
                udpServer.init();
                // 监听UDP 8081端口
                udpServer.start(DEFAULT_UDP_PORT);
            } catch (InterruptedException e) {
                logger.error("UDP server startup failed.", e);
                Thread.currentThread().interrupt();
            }
        }, "UDP-Server-Thread").start();

        // 启动WebSocket服务器
        new Thread(() -> {
            try {
                NettyServerWebSocket webSocketServer = new NettyServerWebSocket(DEFAULT_WEBSOCKET_PATH);
                webSocketServer.init();
                // 监听WebSocket 8082端口
                webSocketServer.start(DEFAULT_WEBSOCKET_PORT);
            } catch (InterruptedException e) {
                logger.error("WebSocket server startup failed.", e);
                Thread.currentThread().interrupt();
            }
        }, "WebSocket-Server-Thread").start();

        logger.info("All servers are starting...");
    }
}