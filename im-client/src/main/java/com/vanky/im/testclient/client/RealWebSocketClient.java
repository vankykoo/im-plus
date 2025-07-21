package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.enums.ClientToClientMessageType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真正的WebSocket客户端实现
 */
public class RealWebSocketClient implements WebSocket.Listener {
    
    private static final String GATEWAY_WS_URL = "ws://localhost:8902/websocket";
    
    private final String userId;
    private final String token;
    private final MessageHandler messageHandler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    private CountDownLatch connectLatch;
    
    private WebSocket webSocket;
    private HttpClient httpClient;
    
    public RealWebSocketClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * 连接到WebSocket服务器
     */
    public void connect() {
        // 重置连接状态
        connected.set(false);
        isLoggedIn.set(false);
        connectLatch = new CountDownLatch(1); // 重新创建CountDownLatch

        try {
            URI uri = URI.create(GATEWAY_WS_URL);
            CompletableFuture<WebSocket> webSocketFuture = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, this);
            
            webSocket = webSocketFuture.get(10, TimeUnit.SECONDS);
            System.out.println("WebSocket连接已建立 - 用户: " + userId);
            
        } catch (Exception e) {
            System.err.println("WebSocket连接失败 - 用户: " + userId + " - " + e.getMessage());
            connectLatch.countDown();
        }
    }
    
    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("WebSocket连接已打开 - 用户: " + userId);
        connected.set(true);
        connectLatch.countDown();
        
        // 发送登录消息
        sendLoginMessage();
        
        webSocket.request(1);
    }
    
    @Override
    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        System.out.println("收到文本消息: " + data);
        webSocket.request(1);
        return null;
    }
    
    @Override
    public CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            // 解析二进制消息
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            
            ChatMessage chatMessage = ChatMessage.parseFrom(bytes);
            System.out.println("收到消息 - 类型: " + chatMessage.getType() + 
                             ", 发送方: " + chatMessage.getFromId() + 
                             ", 内容: " + chatMessage.getContent());
            
            // 处理登录响应
            if (chatMessage.getType() == 1001) { // LOGIN_RESPONSE
                if ("登录成功".equals(chatMessage.getContent()) || chatMessage.getContent().contains("成功")) {
                    isLoggedIn.set(true);
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
        
        webSocket.request(1);
        return null;
    }
    
    @Override
    public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("WebSocket连接已关闭 - 用户: " + userId + 
                         ", 状态码: " + statusCode + ", 原因: " + reason);
        connected.set(false);
        isLoggedIn.set(false);
        return null;
    }
    
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("WebSocket连接错误 - 用户: " + userId + " - " + error.getMessage());
        connected.set(false);
        isLoggedIn.set(false);
        connectLatch.countDown();
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

        System.out.println("发送登录消息 - 用户: " + userId + ", Token: " + token);
        sendBinaryMessage(loginMsg);
    }
    
    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String toUserId, String content) {
        if (!isLoggedIn.get()) {
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
        if (!isLoggedIn.get()) {
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
        if (!isLoggedIn.get()) {
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
            if (!connected.get() || webSocket == null) {
                System.err.println("连接未建立，无法发送消息");
                return;
            }
            
            byte[] data = message.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            webSocket.sendBinary(buffer, true);
            
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查连接状态
     */
    public boolean isOpen() {
        return connected.get();
    }
    
    /**
     * 检查连接状态（别名方法）
     */
    public boolean isConnected() {
        return isOpen() && isLoggedIn.get();
    }
    
    /**
     * 检查登录状态
     */
    public boolean isLoggedIn() {
        return isLoggedIn.get();
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (isOpen() && webSocket != null) {
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
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "客户端主动断开");
        }
        
        connected.set(false);
        isLoggedIn.set(false);
    }
    
    /**
     * 消息处理接口
     */
    public interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}