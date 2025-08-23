package com.vanky.im.testclient.client;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.ReadReceipt;
import com.vanky.im.testclient.manager.PendingMessageManager;
import com.vanky.im.testclient.model.PendingMessage;
import com.vanky.im.testclient.pushpull.UnifiedMessageProcessor;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象客户端基类
 * 封装了 TCP 和 WebSocket 客户端的通用逻辑，包括：
 * - 用户认证信息
 * - 消息发送、接收、确认机制
 * - 心跳机制
 * - 断线重连
 * - 推拉结合的消息处理器
 *
 * @author vanky
 * @since 2025-08-23
 */
public abstract class AbstractClient implements IMClient {

    protected final String userId;
    protected String token;
    protected MessageHandler messageHandler;
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    protected final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    protected CountDownLatch connectLatch;

    protected final HttpClient httpClient;
    protected final LocalMessageStorage localStorage;
    protected final PendingMessageManager pendingMessageManager;
    protected final UnifiedMessageProcessor unifiedMessageProcessor;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;


    public AbstractClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);

        this.httpClient = new HttpClient();
        this.localStorage = new LocalMessageStorage();
        this.pendingMessageManager = new PendingMessageManager();
        this.unifiedMessageProcessor = new UnifiedMessageProcessor(httpClient, localStorage, userId);

        setupCallbacks();
    }

    /**
     * 设置各种回调，将组件连接起来
     */
    private void setupCallbacks() {
        // 待处理消息管理器的回调
        this.pendingMessageManager.setTimeoutCallback(new PendingMessageManager.TimeoutCallback() {
            @Override
            public void onMessageTimeout(PendingMessage message) {
                retryMessage(message);
            }

            @Override
            public void onMessageFailed(PendingMessage message) {
                System.err.println("消息发送最终失败: " + message.getClientSeq() + ", 内容: " + message.getContent());
            }
        });
        this.pendingMessageManager.setLocalStorage(localStorage);
        this.pendingMessageManager.setUserId(userId);

        // 统一消息处理器的回调
        this.unifiedMessageProcessor.setMessageCallback(message -> {
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        });

        this.unifiedMessageProcessor.setMessageDeliveryCallback((clientSeq, uid, serverSeq, conversationSeq) ->
                pendingMessageManager.handleSendReceipt(clientSeq, uid, serverSeq, conversationSeq)
        );
    }

    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void connect() {
        // 重置状态
        connected.set(false);
        isLoggedIn.set(false);
        connectLatch = new CountDownLatch(1);
        
        // 启动待确认消息管理器
        pendingMessageManager.start();

        // 执行连接
        doConnect();
    }

    @Override
    public void disconnect() {
        // 停止心跳和重连任务
        scheduler.shutdownNow();
        
        // 发送登出消息
        if (isLoggedIn.get()) {
            sendLogoutMessage();
        }
        
        // 执行断开连接
        doDisconnect();

        // 清理资源
        connected.set(false);
        isLoggedIn.set(false);
        if (pendingMessageManager != null) {
            pendingMessageManager.stop();
        }
        if (unifiedMessageProcessor != null) {
            unifiedMessageProcessor.shutdown();
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public boolean isLoggedIn() {
        return isLoggedIn.get();
    }

    @Override
    public boolean waitForConnection(long timeout, TimeUnit unit) {
        try {
            return connectLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public void sendPrivateMessage(String toUserId, String content) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }

        String clientSeq = UUID.randomUUID().toString();
        String conversationId = generatePrivateConversationId(userId, toUserId);

        ChatMessage privateMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE)
                .setContent(content)
                .setFromId(userId)
                .setToId(toUserId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .setClientSeq(clientSeq)
                .setConversationId(conversationId)
                .build();

        PendingMessage pendingMessage = new PendingMessage(clientSeq, content, toUserId, MessageTypeConstants.PRIVATE_CHAT_MESSAGE, conversationId);
        if (pendingMessageManager.addPendingMessage(pendingMessage)) {
            sendMessageInternal(privateMsg);
            System.out.println("发送私聊消息 - 从: " + userId + " 到: " + toUserId + ", 内容: " + content + ", 客户端序列号: " + clientSeq);
        } else {
            System.err.println("添加待确认消息失败，消息未发送");
        }
    }

    @Override
    public void sendGroupMessage(String groupId, String content) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }

        String clientSeq = UUID.randomUUID().toString();
        String conversationId = "group_" + groupId;

        ChatMessage groupMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.GROUP_CHAT_MESSAGE)
                .setContent(content)
                .setFromId(userId)
                .setToId(groupId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .setClientSeq(clientSeq)
                .setConversationId(conversationId)
                .build();

        PendingMessage pendingMessage = new PendingMessage(clientSeq, content, groupId, MessageTypeConstants.GROUP_CHAT_MESSAGE, conversationId);
        if (pendingMessageManager.addPendingMessage(pendingMessage)) {
            sendMessageInternal(groupMsg);
            System.out.println("发送群聊消息 - 从: " + userId + " 到群: " + groupId + ", 内容: " + content + ", 客户端序列号: " + clientSeq);
        } else {
            System.err.println("添加待确认消息失败，消息未发送");
        }
    }

    @Override
    public void sendReadReceipt(String conversationId, long lastReadSeq) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送已读回执");
            return;
        }

        ReadReceipt readReceipt = ReadReceipt.newBuilder()
                .setConversationId(conversationId)
                .setLastReadSeq(lastReadSeq)
                .build();

        ChatMessage readReceiptMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_READ_RECEIPT)
                .setFromId(userId)
                .setUid(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis())
                .setReadReceipt(readReceipt)
                .build();

        sendMessageInternal(readReceiptMsg);
        System.out.println("发送已读回执成功 - 用户: " + userId + ", 会话: " + conversationId + ", 已读序列号: " + lastReadSeq);
    }

    @Override
    public void sendBatchAckMessage(List<String> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) return;

        String msgIdsStr = String.join(",", msgIds);
        ChatMessage batchAckMessage = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.BATCH_MESSAGE_ACK)
                .setContent(msgIdsStr)
                .setFromId(userId)
                .setToId("system")
                .setUid("batch_ack_" + System.currentTimeMillis())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .build();

        sendMessageInternal(batchAckMessage);
        System.out.println("发送批量ACK确认 - 消息数量: " + msgIds.size());
    }

    @Override
    public void sendGroupConversationAck(String ackContent) {
        if (ackContent == null || ackContent.trim().isEmpty()) return;

        ChatMessage groupAckMessage = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.GROUP_CONVERSATION_ACK)
                .setContent(ackContent)
                .setFromId(userId)
                .setToId("system")
                .setUid("group_ack_" + System.currentTimeMillis())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .build();

        sendMessageInternal(groupAckMessage);
        System.out.println("发送群聊会话ACK确认 - 内容: " + ackContent);
    }

    @Override
    public String getPendingMessageStatistics() {
        return pendingMessageManager != null ? pendingMessageManager.getStatistics() : "PendingMessageManager未初始化";
    }

    /**
     * 子类必须实现的连接逻辑。
     */
    protected abstract void doConnect();

    /**
     * 子类必须实现的断开连接逻辑。
     */
    protected abstract void doDisconnect();

    /**
     * 子类必须实现的消息发送逻辑。
     *
     * @param message 要发送的消息
     */
    protected abstract void sendMessageInternal(ChatMessage message);

    /**
     * 通用的接收消息处理逻辑。
     *
     * @param chatMessage 接收到的消息
     */
    protected void onMessageReceived(ChatMessage chatMessage) {
        if (chatMessage.getType() != MessageTypeConstants.HEARTBEAT_RESPONSE) {
            System.out.println("收到消息 - 类型: " + chatMessage.getType() + ", 发送方: " + chatMessage.getFromId() + ", 内容: " + chatMessage.getContent());
        }

        switch (chatMessage.getType()) {
            case MessageTypeConstants.LOGIN_RESPONSE:
                handleLoginResponse(chatMessage);
                break;
            case MessageTypeConstants.PRIVATE_CHAT_MESSAGE:
            case MessageTypeConstants.GROUP_CHAT_MESSAGE:
            case MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION:
                unifiedMessageProcessor.processReceivedMessage(chatMessage);
                sendAckMessage(chatMessage);
                break;
            case MessageTypeConstants.MESSAGE_SEND_RECEIPT:
                handleMessageSendReceipt(chatMessage);
                break;
            case MessageTypeConstants.HEARTBEAT_RESPONSE:
                // 心跳响应，无需处理
                break;
            default:
                System.out.println("收到未知类型的消息: " + chatMessage.getType());
                break;
        }
    }

    /**
     * 连接成功后的处理逻辑。
     */
    protected void onConnected() {
        System.out.println("客户端连接成功 - 用户: " + userId);
        connected.set(true);
        connectLatch.countDown();
        sendLoginMessage();
        startHeartbeat();
    }

    /**
     * 连接断开后的处理逻辑。
     */
    protected void onDisconnected() {
        System.out.println("客户端连接断开 - 用户: " + userId);
        connected.set(false);
        isLoggedIn.set(false);
        
        // 尝试重连
        scheduleReconnect();
    }

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
                .build();
        sendMessageInternal(loginMsg);
        System.out.println("发送登录消息 - 用户: " + userId);
    }
    
    private void sendLogoutMessage() {
        ChatMessage logoutMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.LOGOUT_REQUEST)
                .setContent("登出请求")
                .setFromId(userId)
                .setToId("system")
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendMessageInternal(logoutMsg);
        System.out.println("发送登出消息 - 用户: " + userId);
    }

    private void handleLoginResponse(ChatMessage chatMessage) {
        if ("登录成功".equals(chatMessage.getContent()) || chatMessage.getContent().contains("成功")) {
            isLoggedIn.set(true);
            System.out.println("用户 " + userId + " 登录成功");
        } else {
            System.err.println("用户 " + userId + " 登录失败: " + chatMessage.getContent());
            disconnect(); // 登录失败则断开连接
        }
    }

    private void sendAckMessage(ChatMessage originalMessage) {
        String originalMsgId = originalMessage.getUid();
        String originalSeq = originalMessage.getSeq();
        
        ChatMessage ackMessage = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_ACK)
                .setContent("ACK")
                .setFromId(userId)
                .setToId("system")
                .setUid(originalMsgId)
                .setSeq(originalSeq)
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendMessageInternal(ackMessage);
    }

    private void handleMessageSendReceipt(ChatMessage receiptMessage) {
        String clientSeq = receiptMessage.getClientSeq();
        String serverMsgId = receiptMessage.getUid();
        String serverSeq = String.valueOf(receiptMessage.getUserSeq());
        String conversationSeq = String.valueOf(receiptMessage.getConversationSeq());

        System.out.println("收到消息发送回执 - clientSeq: " + clientSeq + ", serverMsgId: " + serverMsgId + ", serverSeq: " + serverSeq + ", conversationSeq: " + conversationSeq);
        pendingMessageManager.handleSendReceipt(clientSeq, serverMsgId, serverSeq, conversationSeq);
    }

    private void retryMessage(PendingMessage message) {
        if (!isLoggedIn.get()) {
            System.err.println("用户未登录，无法重试消息: " + message.getClientSeq());
            return;
        }
        System.out.println("重试发送消息: " + message.getClientSeq() + ", 重试次数: " + message.getRetryCount());

        ChatMessage.Builder builder = ChatMessage.newBuilder()
                .setType(message.getMessageType())
                .setContent(message.getContent())
                .setFromId(userId)
                .setToId(message.getToUserId())
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(message.getRetryCount())
                .setClientSeq(message.getClientSeq())
                .setConversationId(message.getConversationId());

        sendMessageInternal(builder.build());
    }

    private String generatePrivateConversationId(String fromUserId, String toUserId) {
        return fromUserId.compareTo(toUserId) < 0 ? "private_" + fromUserId + "_" + toUserId : "private_" + toUserId + "_" + fromUserId;
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        if (!isLoggedIn.get()) return;
        
        ChatMessage heartbeatMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.HEARTBEAT)
                .setContent("heartbeat")
                .setFromId(userId)
                .setToId("system")
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .build();
        sendMessageInternal(heartbeatMsg);
    }

    private void scheduleReconnect() {
        if (scheduler.isShutdown()) return;
        System.out.println(RECONNECT_DELAY_SECONDS + "秒后尝试重新连接...");
        scheduler.schedule(this::connect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}