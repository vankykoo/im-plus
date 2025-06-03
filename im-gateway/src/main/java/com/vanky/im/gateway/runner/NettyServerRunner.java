package com.vanky.im.gateway.runner;

import com.vanky.im.gateway.config.NettyServerConfig;
import com.vanky.im.gateway.netty.NettyServerTCP;
import com.vanky.im.gateway.netty.NettyServerUDP;
import com.vanky.im.gateway.netty.NettyServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Netty 服务器启动器，在 Spring Boot 应用启动完成后初始化并启动 Netty 服务器
 *
 * @author vanky
 */
@Component
public class NettyServerRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerRunner.class);

    private final NettyServerConfig serverConfig;
    private final NettyServerTCP tcpServer;
    private final NettyServerUDP udpServer;
    private final NettyServerWebSocket webSocketServer;

    @Autowired
    public NettyServerRunner(NettyServerConfig serverConfig, 
                            NettyServerTCP tcpServer, 
                            NettyServerUDP udpServer, 
                            NettyServerWebSocket webSocketServer) {
        this.serverConfig = serverConfig;
        this.tcpServer = tcpServer;
        this.udpServer = udpServer;
        this.webSocketServer = webSocketServer;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Starting Netty servers...");

        // 启动 TCP 服务器
        if (serverConfig.getTcp().isEnabled()) {
            int tcpPort = serverConfig.getTcp().getPort();
            logger.info("Starting TCP server on port: {}", tcpPort);
            tcpServer.start(tcpPort);
        } else {
            logger.info("TCP server is disabled");
        }

        // 启动 UDP 服务器
        if (serverConfig.getUdp().isEnabled()) {
            int udpPort = serverConfig.getUdp().getPort();
            logger.info("Starting UDP server on port: {}", udpPort);
            udpServer.start(udpPort);
        } else {
            logger.info("UDP server is disabled");
        }

        // 启动 WebSocket 服务器
        if (serverConfig.getWebsocket().isEnabled()) {
            int wsPort = serverConfig.getWebsocket().getPort();
            logger.info("Starting WebSocket server on port: {} with path: {}", 
                    wsPort, serverConfig.getWebsocket().getPath());
            webSocketServer.start(wsPort);
        } else {
            logger.info("WebSocket server is disabled");
        }

        logger.info("All Netty servers started successfully");
    }
}