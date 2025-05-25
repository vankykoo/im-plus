package com.vanky.im.client;

import com.vanky.im.client.netty.NettyClientTCP;
import com.vanky.im.client.netty.NettyClientUDP;
import com.vanky.im.client.netty.NettyClientWebSocket;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.MsgGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

import static com.vanky.im.common.constant.PortConstant.DEFAULT_TCP_PORT;
import static com.vanky.im.common.constant.PortConstant.DEFAULT_UDP_PORT;
import static com.vanky.im.common.constant.PortConstant.DEFAULT_WEBSOCKET_PORT;
import static com.vanky.im.common.constant.UriConstant.DEFAULT_HOST;
import static com.vanky.im.common.constant.UriConstant.DEFAULT_WEBSOCKET_PATH;

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
            host = DEFAULT_HOST;
        }

        logger.info("请输入用户ID (用于登录):");
        String userId = scanner.nextLine();
        if (userId.isEmpty()) {
            userId = "user" + System.currentTimeMillis();
            logger.info("未输入用户ID，使用默认ID: {}", userId);
        }

        switch (clientType) {
            case "tcp":
                startTcpClient(scanner, host, userId);
                break;
            case "udp":
                startUdpClient(scanner, host, userId);
                break;
            case "websocket":
                startWebSocketClient(scanner, host, userId);
                break;
            default:
                logger.error("无效的客户端类型: {}", clientType);
                break;
        }
        scanner.close();
    }

    private static void startTcpClient(Scanner scanner, String host, String userId) {
        logger.info("请输入TCP服务器端口 (默认: {}):", DEFAULT_TCP_PORT);
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? DEFAULT_TCP_PORT : Integer.parseInt(portStr);

        NettyClientTCP tcpClient = new NettyClientTCP(host, port);
        try {
            tcpClient.init();
            tcpClient.connect();
            logger.info("TCP客户端已连接到 {}:{}.", host, port);
            
            // 设置用户ID
            tcpClient.setUserId(userId);
            
            // 发送登录消息
            sendLoginMessage(tcpClient, userId);
            
            // 启动心跳
            tcpClient.startHeartbeat();
            logger.info("TCP心跳机制已启动");
            
            logger.info("输入消息内容进行聊天，输入 'exit' 断开连接。");
            while (tcpClient.isConnected()) {
                System.out.println("想发送给谁？");
                String toUserId = scanner.nextLine();
                System.out.println("请输入消息内容：");
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = MsgGenerator.generatePrivateMsg(userId, toUserId, message);
                tcpClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("TCP客户端连接失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            tcpClient.disconnect();
        }
    }

    private static void startUdpClient(Scanner scanner, String host, String userId) {
        logger.info("请输入UDP服务器端口 (默认: {}):", DEFAULT_UDP_PORT);
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? DEFAULT_UDP_PORT : Integer.parseInt(portStr);

        NettyClientUDP udpClient = new NettyClientUDP(host, port);
        try {
            udpClient.init();
            udpClient.connect(); // UDP的connect是绑定本地端口
            logger.info("UDP客户端已准备好与 {}:{} 通信.", host, port);
            
            // 设置用户ID
            udpClient.setUserId(userId);
            
            // 发送登录消息
            sendLoginMessage(udpClient, userId);
            
            // 启动心跳
            udpClient.startHeartbeat();
            logger.info("UDP心跳机制已启动");
            
            logger.info("输入消息内容进行聊天，输入 'exit' 退出。");
            while (true) {
                System.out.println("想发送给谁？");
                String toUserId = scanner.nextLine();
                System.out.println("请输入消息内容：");
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = MsgGenerator.generatePrivateMsg(userId, toUserId, message);
                udpClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("UDP客户端初始化失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            udpClient.disconnect();
        }
    }

    private static void startWebSocketClient(Scanner scanner, String host, String userId) {
        logger.info("请输入WebSocket服务器端口 (默认: {}):", DEFAULT_WEBSOCKET_PORT);
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? DEFAULT_WEBSOCKET_PORT : Integer.parseInt(portStr);

        logger.info("请输入WebSocket路径 (默认: {}):", DEFAULT_WEBSOCKET_PATH);
        String path = scanner.nextLine();
        if (path.isEmpty()) {
            path = DEFAULT_WEBSOCKET_PATH;
        }

        logger.info("是否使用SSL (wss)? (yes/no, 默认: no):");
        boolean useSSL = "yes".equalsIgnoreCase(scanner.nextLine());

        NettyClientWebSocket wsClient = new NettyClientWebSocket(host, port, path, useSSL);
        try {
            wsClient.init();
            wsClient.connect();
            logger.info("WebSocket客户端已连接到 {}:{}{}.", host, port, path);
            
            // 设置用户ID
            wsClient.setUserId(userId);
            
            // 发送登录消息
            sendLoginMessage(wsClient, userId);
            
            // 启动心跳
            wsClient.startHeartbeat();
            logger.info("WebSocket心跳机制已启动");
            
            logger.info("输入消息内容进行聊天，输入 'exit' 断开连接。");
            while (wsClient.isConnected()) {
                System.out.println("想发送给谁？");
                String toUserId = scanner.nextLine();
                System.out.println("请输入消息内容：");
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                ChatMessage msg = MsgGenerator.generatePrivateMsg(userId, toUserId, message);
                wsClient.sendMessage(msg);
            }
        } catch (InterruptedException e) {
            logger.error("WebSocket客户端连接失败.", e);
            Thread.currentThread().interrupt();
        } finally {
            wsClient.disconnect();
        }
    }

    /**
     * 发送登录消息
     * @param client 客户端
     * @param userId 用户ID
     */
    private static void sendLoginMessage(Object client, String userId) {
        ChatMessage loginMsg = MsgGenerator.generateLoginMsg(userId);

        // 根据客户端类型发送登录消息
        if (client instanceof NettyClientTCP) {
            ((NettyClientTCP) client).sendMessage(loginMsg);
            logger.info("已发送TCP登录消息，用户ID: {}", userId);
        } else if (client instanceof NettyClientUDP) {
            ((NettyClientUDP) client).sendMessage(loginMsg);
            logger.info("已发送UDP登录消息，用户ID: {}", userId);
        } else if (client instanceof NettyClientWebSocket) {
            ((NettyClientWebSocket) client).sendMessage(loginMsg);
            logger.info("已发送WebSocket登录消息，用户ID: {}", userId);
        }
    }
}