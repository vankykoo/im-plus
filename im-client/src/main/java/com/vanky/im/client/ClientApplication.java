package com.vanky.im.client;

import com.vanky.im.client.netty.NettyClientTCP;
import com.vanky.im.client.netty.NettyClientUDP;
import com.vanky.im.client.netty.NettyClientWebSocket;
import com.vanky.im.common.protocol.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.UUID;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description 客户端启动类
 */
public class ClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        logger.info("请选择客户端类型 (tcp, udp, websocket):");
        String clientType = scanner.nextLine().toLowerCase();

        logger.info("请输入服务器IP地址 (默认: localhost):");
        String host = scanner.nextLine();
        if (host.isEmpty()) {
            host = "localhost";
        }

        switch (clientType) {
            case "tcp":
                startTcpClient(scanner, host);
                break;
            case "udp":
                startUdpClient(scanner, host);
                break;
            case "websocket":
                startWebSocketClient(scanner, host);
                break;
            default:
                logger.error("无效的客户端类型: {}", clientType);
                break;
        }
        scanner.close();
    }

    private static void startTcpClient(Scanner scanner, String host) {
        logger.info("请输入TCP服务器端口 (默认: 8080):");
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? 8080 : Integer.parseInt(portStr);

        NettyClientTCP tcpClient = new NettyClientTCP(host, port);
        try {
            tcpClient.init();
            tcpClient.connect();
            logger.info("TCP客户端已连接到 {}:{}. 输入 'exit' 断开连接.", host, port);
            while (tcpClient.isConnected()) {
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = buildChatMessage(message);
                tcpClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("TCP客户端连接失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            tcpClient.disconnect();
        }
    }

    private static void startUdpClient(Scanner scanner, String host) {
        logger.info("请输入UDP服务器端口 (默认: 8081):");
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? 8081 : Integer.parseInt(portStr);

        NettyClientUDP udpClient = new NettyClientUDP(host, port);
        try {
            udpClient.init();
            udpClient.connect(); // UDP的connect是绑定本地端口
            logger.info("UDP客户端已准备好与 {}:{} 通信. 输入 'exit' 退出.", host, port);
            while (true) {
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = buildChatMessage(message);
                udpClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("UDP客户端初始化失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            udpClient.disconnect();
        }
    }

    private static void startWebSocketClient(Scanner scanner, String host) {
        logger.info("请输入WebSocket服务器端口 (默认: 8082):");
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? 8082 : Integer.parseInt(portStr);

        logger.info("请输入WebSocket路径 (默认: /websocket):");
        String path = scanner.nextLine();
        if (path.isEmpty()) {
            path = "/websocket";
        }

        logger.info("是否使用SSL (wss)? (yes/no, 默认: no):");
        boolean useSSL = "yes".equalsIgnoreCase(scanner.nextLine());

        NettyClientWebSocket wsClient = new NettyClientWebSocket(host, port, path, useSSL);
        try {
            wsClient.init();
            wsClient.connect();
            logger.info("WebSocket客户端已连接到 {}:{}{}. 输入 'exit' 断开连接.", host, port, path);
            while (wsClient.isConnected()) {
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = buildChatMessage(message);
                wsClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("WebSocket客户端连接失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            wsClient.disconnect();
        }
    }

    private static ChatMessage buildChatMessage(String content) {
        return ChatMessage.newBuilder()
                .setType(1)
                .setContent(content)
                .setFromId("client")
                .setToId("server")
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }
}