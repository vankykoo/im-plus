package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.enums.ClientToClientMessageType;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * WebSocket客户端，用于连接IM网关
 */
public class IMWebSocketClient {
    
    private static final String GATEWAY_WS_URL = "ws://localhost:8902/websocket";
    
    private final String userId;
    private final String token;
    private final MessageHandler messageHandler;
    private CountDownLatch connectLatch;
    private boolean isLoggedIn = false;
    
    private boolean connected = false;
    
    public IMWebSocketClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);
    }
    
    /**
     * 连接到服务器
     */
    public void connect() {
        try {
            System.out.println("WebSocket连接已建立 - 用户: " + userId);
            connected = true;
            connectLatch.countDown();
            
            // 发送登录消息
            sendLoginMessage();
        } catch (Exception e) {
            System.err.println("WebSocket连接错误 - 用户: " + userId + " - " + e.getMessage());
        }
    }
    
    /**
     * 处理接收到的消息
     */
    public void onMessage(String message) {
        System.out.println("收到文本消息: " + message);
    }
    
    /**
     * 处理接收到的二进制消息
     */
    public void onMessage(ByteBuffer bytes) {
        try {
            // 解析二进制消息
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            
            ChatMessage chatMessage = ChatMessage.parseFrom(data);
            System.out.println("收到消息 - 类型: " + chatMessage.getType() + ", 发送方: " + chatMessage.getFromId() + ", 内容: " + chatMessage.getContent());
            
            // 处理登录响应
            if (chatMessage.getType() == 1001) { // LOGIN_RESPONSE
                if ("登录成功".equals(chatMessage.getContent())) {
                    isLoggedIn = true;
                    System.out.println("用户 " + userId + " 登录成功");
                } else {
                    System.err.println("用户 " + userId + " 登录失败: " + chatMessage.getContent());
                }
            }
            
            // 委托给消息处理器
            if (messageHandler != null) {
                messageHandler.handleMessage(chatMessage);
            }
            
        } catch (Exception e) {
            System.err.println("解析消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 连接关闭处理
     */
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WebSocket连接已关闭 - 用户: " + userId + ", 代码: " + code + ", 原因: " + reason + ", 远程关闭: " + remote);
        connected = false;
        isLoggedIn = false;
    }
    
    /**
     * 错误处理
     */
    public void onError(Exception ex) {
        System.err.println("WebSocket连接错误 - 用户: " + userId + " - " + ex.getMessage());
    }
    
    /**
     * 等待连接建立
     */
    public boolean waitForConnection(long timeout, TimeUnit unit) {
        try {
            return connectLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * 发送登录消息
     */
    private void sendLoginMessage() {
        ChatMessage loginMsg = ChatMessage.newBuilder()
                .setType(ClientToServerMessageType.LOGIN_REQUEST.getValue())
                .setContent("登录请求")
                .setFromId(userId)
                .setToId("system")
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setToken(token)
                .setRetry(0)
                .build();
        
        sendBinaryMessage(loginMsg);
    }
    
    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String toUserId, String content) {
        if (!isLoggedIn) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }
        
        ChatMessage privateMsg = ChatMessage.newBuilder()
                .setType(ClientToClientMessageType.PRIVATE_CHAT_MESSAGE.getValue())
                .setContent(content)
                .setFromId(userId)
                .setToId(toUserId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
        
        sendBinaryMessage(privateMsg);
        System.out.println("发送私聊消息 - 从: " + userId + " 到: " + toUserId + ", 内容: " + content);
    }
    
    /**
     * 发送群聊消息
     */
    public void sendGroupMessage(String groupId, String content) {
        if (!isLoggedIn) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }
        
        ChatMessage groupMsg = ChatMessage.newBuilder()
                .setType(ClientToClientMessageType.GROUP_CHAT_MESSAGE.getValue())
                .setContent(content)
                .setFromId(userId)
                .setToId(groupId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
        
        sendBinaryMessage(groupMsg);
        System.out.println("发送群聊消息 - 从: " + userId + " 到群: " + groupId + ", 内容: " + content);
    }
    
    /**
     * 发送心跳消息
     */
    public void sendHeartbeat() {
        if (!isLoggedIn) {
            return;
        }
        
        ChatMessage heartbeatMsg = ChatMessage.newBuilder()
                .setType(ClientToServerMessageType.HEARTBEAT.getValue())
                .setContent("heartbeat")
                .setFromId(userId)
                .setToId("system")
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
        
        sendBinaryMessage(heartbeatMsg);
    }
    
    /**
     * 发送二进制消息
     */
    private void sendBinaryMessage(ChatMessage message) {
        try {
            if (!connected) {
                System.err.println("连接未建立，无法发送消息");
                return;
            }
            byte[] data = message.toByteArray();
            // 模拟发送消息
            System.out.println("发送消息: " + message.getContent());
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查连接状态
     */
    public boolean isOpen() {
        return connected;
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        connected = false;
        System.out.println("连接已关闭");
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (isOpen()) {
            // 发送登出消息
            ChatMessage logoutMsg = ChatMessage.newBuilder()
                    .setType(ClientToServerMessageType.LOGOUT_REQUEST.getValue())
                    .setContent("登出请求")
                    .setFromId(userId)
                    .setToId("system")
                    .setUid(UUID.randomUUID().toString())
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();
            
            sendBinaryMessage(logoutMsg);
            
            // 关闭连接
            close();
        }
        isLoggedIn = false;
    }
    
    public boolean isLoggedIn() {
        return isLoggedIn;
    }
    
    /**
     * 消息处理接口
     */
    public interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}