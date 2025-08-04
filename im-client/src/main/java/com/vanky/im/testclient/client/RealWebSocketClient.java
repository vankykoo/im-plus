package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.storage.LocalMessageStorage;
import java.util.List;

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

    // 用于更新本地同步点
    private com.vanky.im.testclient.client.HttpClient imHttpClient;
    private LocalMessageStorage localStorage;
    
    public RealWebSocketClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 初始化IM HTTP客户端和本地存储
        this.imHttpClient = new com.vanky.im.testclient.client.HttpClient();
        this.localStorage = new LocalMessageStorage();
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

        // 延迟发送登录消息，确保连接完全建立
        new Thread(() -> {
            try {
                Thread.sleep(100); // 延迟100ms
                sendLoginMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

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
            // 处理聊天消息，需要发送ACK确认
            else if (chatMessage.getType() == MessageTypeConstants.PRIVATE_CHAT_MESSAGE ||
                     chatMessage.getType() == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
                // 发送ACK确认消息
                sendAckMessage(chatMessage.getUid(), chatMessage.getSeq());

                // 更新本地用户级全局seq，避免重复拉取
                updateLocalSyncSeqForRealTimeMessage(chatMessage);
            }
            // 处理群聊消息通知，需要发送群聊会话ACK确认
            else if (chatMessage.getType() == MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION) {
                // 1. 更新客户端本地的群聊同步点
                updateClientGroupConversationSeq(chatMessage);

                // 2. 发送群聊会话ACK确认
                sendGroupConversationAckForNotification(chatMessage);

                System.out.println("收到群聊消息通知 - 会话ID: " + chatMessage.getConversationId() +
                                 ", 发送方: " + chatMessage.getFromId() +
                                 ", 内容: " + chatMessage.getContent());
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
                .setType(MessageTypeConstants.LOGIN_REQUEST)
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
                .setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE)
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
                .setType(MessageTypeConstants.GROUP_CHAT_MESSAGE)
                .setContent(content)
                .setFromId(userId)
                .setToId(groupId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .setConversationId("group_" + groupId) // 设置会话ID
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
                .setType(MessageTypeConstants.HEARTBEAT)
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
                    .setType(MessageTypeConstants.LOGOUT_REQUEST)
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
     * 发送ACK确认消息
     * @param originalMsgId 原始消息ID
     * @param originalSeq 原始消息序列号
     */
    private void sendAckMessage(String originalMsgId, String originalSeq) {
        try {
            ChatMessage ackMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.MESSAGE_ACK)
                    .setContent("ACK")
                    .setFromId(userId)
                    .setToId("system")
                    .setUid(originalMsgId) // 使用原始消息ID
                    .setSeq(originalSeq)   // 使用原始消息序列号
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();

            sendBinaryMessage(ackMessage);
            System.out.println("发送ACK确认 - 消息ID: " + originalMsgId + ", 序列号: " + originalSeq);

        } catch (Exception e) {
            System.err.println("发送ACK确认失败: " + e.getMessage());
        }
    }

    /**
     * 更新本地用户级全局seq，避免实时推送的消息被重复拉取
     * @param chatMessage 接收到的聊天消息
     */
    private void updateLocalSyncSeqForRealTimeMessage(ChatMessage chatMessage) {
        try {
            // 获取当前本地同步点
            Long currentLocalSeq = localStorage.getLastSyncSeq(userId);

            // 使用当前本地同步点查询服务端
            if (imHttpClient != null) {
                com.vanky.im.testclient.client.HttpClient.SyncCheckResponse response = imHttpClient.checkSyncNeeded(userId, currentLocalSeq);
                if (response != null && response.isSuccess() && response.getTargetSeq() != null) {
                    Long serverMaxSeq = response.getTargetSeq();
                    if (serverMaxSeq > currentLocalSeq) {
                        localStorage.updateLastSyncSeq(userId, serverMaxSeq);
                        System.out.println("更新本地同步点 - 用户ID: " + userId +
                                         ", 当前序列号: " + currentLocalSeq +
                                         ", 新序列号: " + serverMaxSeq);
                    } else {
                        System.out.println("本地同步点已是最新 - 用户ID: " + userId +
                                         ", 当前序列号: " + currentLocalSeq);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("更新本地同步点失败: " + e.getMessage());
        }
    }

    /**
     * 发送批量ACK确认消息
     * @param msgIds 消息ID列表
     */
    public void sendBatchAckMessage(List<String> msgIds) {
        try {
            if (msgIds == null || msgIds.isEmpty()) {
                return;
            }

            // 将消息ID列表转换为逗号分隔的字符串
            String msgIdsStr = String.join(",", msgIds);

            ChatMessage batchAckMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.BATCH_MESSAGE_ACK)
                    .setContent(msgIdsStr) // 消息内容包含所有消息ID
                    .setFromId(userId)
                    .setToId("system")
                    .setUid("batch_ack_" + System.currentTimeMillis()) // 生成唯一ID
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();

            sendBinaryMessage(batchAckMessage);
            System.out.println("发送批量ACK确认 - 消息数量: " + msgIds.size());

        } catch (Exception e) {
            System.err.println("发送批量ACK确认失败: " + e.getMessage());
        }
    }

    /**
     * 发送群聊会话ACK确认
     * @param ackContent ACK内容，格式：conversationId1:seq1,conversationId2:seq2
     */
    public void sendGroupConversationAck(String ackContent) {
        try {
            if (ackContent == null || ackContent.trim().isEmpty()) {
                System.err.println("群聊会话ACK内容为空");
                return;
            }

            ChatMessage groupAckMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.GROUP_CONVERSATION_ACK)
                    .setContent(ackContent) // 消息内容：conversationId1:seq1,conversationId2:seq2
                    .setFromId(userId)
                    .setToId("system")
                    .setUid("group_ack_" + System.currentTimeMillis()) // 生成唯一ID
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();

            sendBinaryMessage(groupAckMessage);
            System.out.println("发送群聊会话ACK确认 - 内容: " + ackContent);

        } catch (Exception e) {
            System.err.println("发送群聊会话ACK确认失败: " + e.getMessage());
        }
    }

    /**
     * 针对群聊消息通知发送群聊会话ACK确认
     * @param notificationMessage 群聊消息通知
     */
    private void sendGroupConversationAckForNotification(ChatMessage notificationMessage) {
        try {
            String conversationId = notificationMessage.getConversationId();
            String seq = notificationMessage.getSeq();

            if (conversationId == null || conversationId.trim().isEmpty()) {
                System.err.println("群聊消息通知缺少会话ID，无法发送ACK");
                return;
            }

            if (seq == null || seq.trim().isEmpty()) {
                System.err.println("群聊消息通知缺少序列号，无法发送ACK");
                return;
            }

            // 构建ACK内容：conversationId:seq
            String ackContent = conversationId + ":" + seq;

            ChatMessage groupAckMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.GROUP_CONVERSATION_ACK)
                    .setContent(ackContent) // 消息内容：conversationId:seq
                    .setFromId(userId)
                    .setToId("system")
                    .setUid("group_notification_ack_" + System.currentTimeMillis()) // 生成唯一ID
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();

            sendBinaryMessage(groupAckMessage);
            System.out.println("发送群聊通知ACK确认 - 会话ID: " + conversationId + ", seq: " + seq);

        } catch (Exception e) {
            System.err.println("发送群聊会话ACK确认失败: " + e.getMessage());
        }
    }

    /**
     * 更新客户端本地的群聊同步点
     * 客户端接收到群聊消息通知时，需要更新自己在Redis中存储的已接收的最大会话seq
     *
     * @param notificationMessage 群聊消息通知
     */
    private void updateClientGroupConversationSeq(ChatMessage notificationMessage) {
        try {
            String conversationId = notificationMessage.getConversationId();
            String seqStr = notificationMessage.getSeq();

            if (conversationId == null || conversationId.trim().isEmpty()) {
                System.err.println("群聊消息通知缺少会话ID，无法更新本地同步点");
                return;
            }

            if (seqStr == null || seqStr.trim().isEmpty()) {
                System.err.println("群聊消息通知缺少序列号，无法更新本地同步点");
                return;
            }

            Long seq;
            try {
                seq = Long.parseLong(seqStr);
            } catch (NumberFormatException e) {
                System.err.println("群聊消息通知序列号格式错误: " + seqStr);
                return;
            }

            // 更新客户端本地的群聊同步点
            // 使用LocalMessageStorage的updateConversationSeq方法
            if (localStorage != null) {
                localStorage.updateConversationSeq(userId, conversationId, seq);
                System.out.println("更新客户端群聊同步点成功 - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId + ", seq: " + seq);
            } else {
                System.err.println("LocalMessageStorage未初始化，无法更新群聊同步点");
            }

        } catch (Exception e) {
            System.err.println("更新客户端群聊同步点失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 消息处理接口
     */
    public interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}