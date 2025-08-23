package com.vanky.im.testclient.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.vanky.im.common.protocol.ChatMessage;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Java 11+ 内置 WebSocket API 的客户端实现。
 *
 * @author vanky
 * @since 2025-08-23
 */
public class RealWebSocketClient extends AbstractClient implements WebSocket.Listener {

    private static final String SERVER_IP = ClientConfig.getProperty("server.base.ip", "localhost");
    private static final String WEBSOCKET_PORT = ClientConfig.getProperty("websocket.port", "80");
    private static final String GATEWAY_WS_URL = "ws://" + SERVER_IP + ":" + WEBSOCKET_PORT + "/websocket";

    private volatile WebSocket webSocket;
    private final java.net.http.HttpClient httpClient;
    private final ScheduledExecutorService livenessChecker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong lastMessageTimestamp = new AtomicLong(0);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private static final int LIVENESS_CHECK_INTERVAL_SECONDS = 10;
    private static final int CONNECTION_TIMEOUT_SECONDS = 45; // 应该比心跳间隔长

    public RealWebSocketClient(String userId, String token, MessageHandler messageHandler) {
        super(userId, token, messageHandler);
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    protected void doConnect() {
        closing.set(false);
        try {
            String urlWithToken = GATEWAY_WS_URL + "?token=" + token + "&userId=" + userId;
            URI uri = URI.create(urlWithToken);
            CompletableFuture<WebSocket> webSocketFuture = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .subprotocols("chat") // 指定聊天子协议
                    .buildAsync(uri, this);

            // 等待WebSocket连接完成，这是修复竞态条件的关键
            // .get()会阻塞，直到CompletableFuture完成
            webSocketFuture.get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println("WebSocket 连接失败 - 用户: " + userId + " - " + e.getMessage());
            onDisconnected(); // 连接失败时触发重连
        }
    }



    @Override
    protected void doDisconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "客户端主动断开");
        }
        livenessChecker.shutdownNow();
    }

    @Override
    protected void sendMessageInternal(ChatMessage message) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            byte[] data = message.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            webSocket.sendBinary(buffer, true);
        } else {
            System.err.println("WebSocket 不可用，无法发送消息 - 类型: " + message.getType());
        }
    }

    // --- WebSocket.Listener 回调实现 ---

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("WebSocket 连接已打开 - 用户: " + userId);
        this.webSocket = webSocket;
        webSocket.request(1);
        lastMessageTimestamp.set(System.currentTimeMillis());
        startLivenessCheck();
        // 调用基类的 onConnected 方法
        onConnected();
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            lastMessageTimestamp.set(System.currentTimeMillis());
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            ChatMessage chatMessage = ChatMessage.parseFrom(bytes);
            // 交由基类统一处理
            onMessageReceived(chatMessage);
        } catch (InvalidProtocolBufferException e) {
            System.err.println("解析 WebSocket 二进制消息失败: " + e.getMessage());
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("WebSocket 连接已关闭 - 用户: " + userId + ", 状态码: " + statusCode + ", 原因: " + reason);
        // 调用基类的 onDisconnected 方法
        if (closing.compareAndSet(false, true)) {
            onDisconnected();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("WebSocket 连接错误 - 用户: " + userId + " - " + error.getMessage());
        // 调用基类的 onDisconnected 方法
        if (closing.compareAndSet(false, true)) {
            onDisconnected();
        }
    }

    private void startLivenessCheck() {
        if (livenessChecker.isShutdown()) return;
        livenessChecker.scheduleAtFixedRate(this::checkLiveness, LIVENESS_CHECK_INTERVAL_SECONDS, LIVENESS_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void checkLiveness() {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastMessageTime = lastMessageTimestamp.get();
        if (now - lastMessageTime > TimeUnit.SECONDS.toMillis(CONNECTION_TIMEOUT_SECONDS)) {
            System.err.println("WebSocket 连接超时 (超过 " + CONNECTION_TIMEOUT_SECONDS + " 秒未收到消息)，主动断开 - 用户: " + userId);
            // 主动关闭连接，这将触发 onClose -> onDisconnected -> 重连
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "连接超时");
            // 立即调用 onDisconnected，以防 sendClose 异步且延迟
            if (closing.compareAndSet(false, true)) {
                onDisconnected();
            }
        }
    }
}