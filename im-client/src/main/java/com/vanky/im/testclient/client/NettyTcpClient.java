package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.ReadReceipt;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.storage.LocalMessageStorage;
import com.vanky.im.testclient.manager.PendingMessageManager;

import com.vanky.im.testclient.model.PendingMessage;
import com.vanky.im.testclient.model.MessageStatus;
import com.vanky.im.testclient.pushpull.UnifiedMessageProcessor;
import java.util.List;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import com.vanky.im.common.protocol.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocol.codec.ProtobufMessageEncoder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty TCP客户端实现
 */
public class NettyTcpClient {
    
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 8900;
    
    private final String userId;
    private final String token;
    private final MessageHandler messageHandler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    private CountDownLatch connectLatch;
    
    private EventLoopGroup group;
    private Channel channel;

    // 用于更新本地同步点
    private HttpClient httpClient;
    private LocalMessageStorage localStorage;

    // 消息发送确认机制
    private PendingMessageManager pendingMessageManager;

    // 推拉结合模式的统一消息处理器
    private UnifiedMessageProcessor unifiedMessageProcessor;
    
    public NettyTcpClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);

        // 初始化HTTP客户端和本地存储
        this.httpClient = new HttpClient();
        this.localStorage = new LocalMessageStorage();

        // 初始化消息发送确认机制
        this.pendingMessageManager = new PendingMessageManager();

        // 设置超时回调
        this.pendingMessageManager.setTimeoutCallback(new PendingMessageManager.TimeoutCallback() {
            @Override
            public void onMessageTimeout(PendingMessage message) {
                // 消息超时，重新发送
                retryMessage(message);
            }

            @Override
            public void onMessageFailed(PendingMessage message) {
                // 消息发送失败
                System.err.println("消息发送失败: " + message.getClientSeq() +
                                 ", 内容: " + message.getContent());
            }
        });

        // 设置本地存储和用户ID
        this.pendingMessageManager.setLocalStorage(localStorage);
        this.pendingMessageManager.setUserId(userId);

        // 初始化统一消息处理器
        this.unifiedMessageProcessor = new UnifiedMessageProcessor(httpClient, localStorage, userId);

        // 设置消息处理回调
        this.unifiedMessageProcessor.setMessageCallback(new UnifiedMessageProcessor.MessageProcessCallback() {
            @Override
            public void onMessageReceived(ChatMessage message) {
                // 委托给原有的消息处理器
                if (messageHandler != null) {
                    messageHandler.handleMessage(message);
                }
            }
        });

        // 设置消息投递回调（统一推送逻辑：发送方接收到自己的消息时更新状态）
        this.unifiedMessageProcessor.setMessageDeliveryCallback(new UnifiedMessageProcessor.MessageDeliveryCallback() {
            @Override
            public boolean onMessageDelivered(String clientSeq, String uid, String serverSeq) {
                // 委托给待确认消息管理器处理
                return pendingMessageManager.handleSendReceipt(clientSeq, uid, serverSeq);
            }
        });
    }
    
    /**
     * 连接到TCP服务器
     */
    public void connect() {
        // 重置连接状态
        connected.set(false);
        isLoggedIn.set(false);
        connectLatch = new CountDownLatch(1); // 重新创建CountDownLatch

        group = new NioEventLoopGroup();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 添加空闲状态处理器
                            pipeline.addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
                            
                            // 添加Protobuf编解码器
                            pipeline.addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                            pipeline.addLast(new ProtobufMessageEncoder());
                            
                            // 添加消息处理器
                            pipeline.addLast(new TcpClientHandler());
                        }
                    });
            
            ChannelFuture future = bootstrap.connect(GATEWAY_HOST, GATEWAY_PORT).sync();
            channel = future.channel();
            
            System.out.println("TCP连接已建立 - 用户: " + userId);
            connected.set(true);
            connectLatch.countDown();

            // 启动待确认消息管理器
            pendingMessageManager.start();
            
        } catch (Exception e) {
            System.err.println("TCP连接失败 - 用户: " + userId + " - " + e.getMessage());
            connectLatch.countDown();
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }
    
    /**
     * TCP客户端处理器
     */
    private class TcpClientHandler extends SimpleChannelInboundHandler<ChatMessage> {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("TCP连接已激活 - 用户: " + userId);
            // 发送登录消息
            sendLoginMessage();
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ChatMessage chatMessage) throws Exception {
            if (chatMessage.getType() != 2003) {
                System.out.println("收到消息 - 类型: " + chatMessage.getType() +
                        ", 发送方: " + chatMessage.getFromId() +
                        ", 内容: " + chatMessage.getContent());
            }
            
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
                // 使用统一消息处理器处理消息（包含推拉结合逻辑）
                unifiedMessageProcessor.processReceivedMessage(chatMessage);

                // 发送ACK确认消息
                sendAckMessage(chatMessage.getUid(), chatMessage.getSeq());
            }
            // 处理群聊消息通知，需要发送群聊会话ACK确认
            else if (chatMessage.getType() == MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION) {
                // 使用统一消息处理器处理群聊通知（包含推拉结合逻辑）
                unifiedMessageProcessor.processReceivedMessage(chatMessage);

                // 发送群聊会话ACK确认
                sendGroupConversationAckForNotification(chatMessage);

                System.out.println("收到群聊消息通知 - 会话ID: " + chatMessage.getConversationId() +
                                 ", 发送方: " + chatMessage.getFromId() +
                                 ", 内容: " + chatMessage.getContent());
            }
            // 处理消息发送回执（端到端确认机制）
            else if (chatMessage.getType() == MessageTypeConstants.MESSAGE_SEND_RECEIPT) {
                handleMessageSendReceipt(chatMessage);
            }

            // {{CHENGQI:
            // Action: Removed; Timestamp: 2025-08-06 22:01:47 +08:00; Reason: 删除重复的messageHandler调用，避免消息重复显示，消息已通过UnifiedMessageProcessor回调处理;
            // }}
            // {{START MODIFICATIONS}}
            // 注意：不再直接调用messageHandler.handleMessage()，因为消息已经通过UnifiedMessageProcessor的回调机制处理
            // 这样可以避免消息重复显示的问题
            // {{END MODIFICATIONS}}
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("TCP连接已断开 - 用户: " + userId);
            connected.set(false);
            isLoggedIn.set(false);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.err.println("TCP连接异常 - 用户: " + userId + " - " + cause.getMessage());
            ctx.close();
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    // 发送心跳
                    sendHeartbeat();
                }
            }
            super.userEventTriggered(ctx, evt);
        }
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
        if (channel == null || !channel.isActive()) {
            System.err.println("Channel不可用，无法发送登录消息 - 用户: " + userId);
            return;
        }

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

        channel.writeAndFlush(loginMsg).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("登录消息发送成功 - 用户: " + userId);
            } else {
                System.err.println("登录消息发送失败 - 用户: " + userId + ", 原因: " + future.cause().getMessage());
            }
        });
        System.out.println("发送登录消息 - 用户: " + userId + ", Token: " + token);
    }
    
    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String toUserId, String content) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }

        // 生成客户端序列号
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
                .setClientSeq(clientSeq) // 设置客户端序列号
                .setConversationId(conversationId)
                .build();

        // 创建待确认消息
        PendingMessage pendingMessage = new PendingMessage(
                clientSeq, content, toUserId,
                MessageTypeConstants.PRIVATE_CHAT_MESSAGE, conversationId);

        // 添加到待确认队列
        if (pendingMessageManager.addPendingMessage(pendingMessage)) {
            channel.writeAndFlush(privateMsg);
            System.out.println("发送私聊消息 - 从: " + userId + " 到: " + toUserId +
                             ", 内容: " + content + ", 客户端序列号: " + clientSeq);
        } else {
            System.err.println("添加待确认消息失败，消息未发送");
        }
    }
    
    /**
     * 发送群聊消息
     */
    public void sendGroupMessage(String groupId, String content) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送消息");
            return;
        }

        // 生成客户端序列号
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
                .setClientSeq(clientSeq) // 设置客户端序列号
                .setConversationId(conversationId)
                .build();

        // 创建待确认消息
        PendingMessage pendingMessage = new PendingMessage(
                clientSeq, content, groupId,
                MessageTypeConstants.GROUP_CHAT_MESSAGE, conversationId);

        // 添加到待确认队列
        if (pendingMessageManager.addPendingMessage(pendingMessage)) {
            channel.writeAndFlush(groupMsg);
            System.out.println("发送群聊消息 - 从: " + userId + " 到群: " + groupId +
                             ", 内容: " + content + ", 客户端序列号: " + clientSeq);
        } else {
            System.err.println("添加待确认消息失败，消息未发送");
        }
    }
    
    /**
     * 发送心跳消息
     */
    public void sendHeartbeat() {
        
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
        
        channel.writeAndFlush(heartbeatMsg);
    }
    

    
    /**
     * 检查连接状态
     */
    public boolean isOpen() {
        return connected.get() && channel != null && channel.isActive();
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected.get() && channel != null && channel.isActive();
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
        if (isOpen()) {
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
            
            channel.writeAndFlush(logoutMsg);
            
            // 关闭连接
            if (channel != null) {
                channel.close();
            }
        }
        
        connected.set(false);
        isLoggedIn.set(false);

        // 停止待确认消息管理器
        if (pendingMessageManager != null) {
            pendingMessageManager.stop();
        }

        // 关闭统一消息处理器
        if (unifiedMessageProcessor != null) {
            unifiedMessageProcessor.shutdown();
        }

        if (group != null) {
            group.shutdownGracefully();
        }
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

            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(ackMessage);
                System.out.println("发送ACK确认 - 消息ID: " + originalMsgId + ", 序列号: " + originalSeq);
            }

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
            if (httpClient != null) {
                HttpClient.SyncCheckResponse response = httpClient.checkSyncNeeded(userId, currentLocalSeq);
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

            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(batchAckMessage);
                System.out.println("发送批量ACK确认 - 消息数量: " + msgIds.size());
            }

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

            if (channel != null && channel.isActive()) {
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

                channel.writeAndFlush(groupAckMessage);
                System.out.println("发送群聊会话ACK确认 - 内容: " + ackContent);
            } else {
                System.err.println("TCP连接不可用，无法发送群聊会话ACK");
            }

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

            if (channel != null && channel.isActive()) {
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

                channel.writeAndFlush(groupAckMessage);
                System.out.println("发送群聊通知ACK确认 - 会话ID: " + conversationId + ", seq: " + seq);
            } else {
                System.err.println("TCP连接不可用，无法发送群聊通知ACK");
            }

        } catch (Exception e) {
            System.err.println("发送群聊通知ACK确认失败: " + e.getMessage());
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
     * 生成私聊会话ID
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     * @return 会话ID
     */
    private String generatePrivateConversationId(String fromUserId, String toUserId) {
        // 私聊会话ID规则：private_xx_xx，其中userid小的放前面
        try {
            long id1 = Long.parseLong(fromUserId);
            long id2 = Long.parseLong(toUserId);

            if (id1 < id2) {
                return "private_" + id1 + "_" + id2;
            } else {
                return "private_" + id2 + "_" + id1;
            }
        } catch (NumberFormatException e) {
            // 如果ID不是数字，则使用字符串比较
            if (fromUserId.compareTo(toUserId) < 0) {
                return "private_" + fromUserId + "_" + toUserId;
            } else {
                return "private_" + toUserId + "_" + fromUserId;
            }
        }
    }

    /**
     * 重试发送消息
     * @param message 待重试的消息
     */
    private void retryMessage(PendingMessage message) {
        if (!isLoggedIn.get()) {
            System.err.println("用户未登录，无法重试消息: " + message.getClientSeq());
            return;
        }

        System.out.println("重试发送消息: " + message.getClientSeq() +
                         ", 重试次数: " + message.getRetryCount());

        // 根据消息类型重新发送
        if (message.getMessageType() == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
            // 重新构建私聊消息
            ChatMessage retryMsg = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE)
                    .setContent(message.getContent())
                    .setFromId(userId)
                    .setToId(message.getToUserId())
                    .setUid(UUID.randomUUID().toString())
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(message.getRetryCount())
                    .setClientSeq(message.getClientSeq()) // 保持相同的客户端序列号
                    .setConversationId(message.getConversationId())
                    .build();

            channel.writeAndFlush(retryMsg);
        } else if (message.getMessageType() == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
            // 重新构建群聊消息
            ChatMessage retryMsg = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.GROUP_CHAT_MESSAGE)
                    .setContent(message.getContent())
                    .setFromId(userId)
                    .setToId(message.getToUserId())
                    .setUid(UUID.randomUUID().toString())
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(message.getRetryCount())
                    .setClientSeq(message.getClientSeq()) // 保持相同的客户端序列号
                    .setConversationId(message.getConversationId())
                    .build();

            channel.writeAndFlush(retryMsg);
        }
    }

    /**
     * 获取待确认消息队列统计信息
     * @return 统计信息
     */
    public String getPendingMessageStatistics() {
        if (pendingMessageManager != null) {
            return pendingMessageManager.getStatistics();
        }
        return "PendingMessageManager未初始化";
    }

    /**
     * 发送已读回执
     */
    public void sendReadReceipt(String conversationId, long lastReadSeq) {
        if (!isLoggedIn.get()) {
            System.err.println("用户 " + userId + " 未登录，无法发送已读回执");
            return;
        }

        if (channel == null || !channel.isActive()) {
            System.err.println("TCP连接不可用，无法发送已读回执");
            return;
        }

        // 构建已读回执消息体
        ReadReceipt readReceipt = ReadReceipt.newBuilder()
                .setConversationId(conversationId)
                .setLastReadSeq(lastReadSeq)
                .build();

        // 构建已读回执消息
        ChatMessage readReceiptMsg = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_READ_RECEIPT)
                .setFromId(userId)
                .setUid(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis())
                .setReadReceipt(readReceipt)
                .build();

        channel.writeAndFlush(readReceiptMsg).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("发送已读回执成功 - 用户: " + userId + ", 会话: " + conversationId + ", 已读序列号: " + lastReadSeq);
            } else {
                System.err.println("发送已读回执失败 - 用户: " + userId + ", 原因: " + future.cause().getMessage());
            }
        });
    }

    /**
     * 处理消息发送回执
     *
     * 按照技术方案要求，处理MESSAGE_SEND_RECEIPT类型的回执消息
     * 使用clientSeq匹配待确认消息，更新本地状态
     *
     * @param receiptMessage 回执消息
     */
    private void handleMessageSendReceipt(ChatMessage receiptMessage) {
        String clientSeq = receiptMessage.getClientSeq();
        String serverMsgId = receiptMessage.getUid();
        Long userSeq = receiptMessage.getUserSeq();

        System.out.println("收到消息发送回执 - 客户端序列号: " + clientSeq +
                         ", 服务端消息ID: " + serverMsgId +
                         ", 用户序列号: " + userSeq);

        try {
            // 使用PendingMessageManager处理回执
            boolean success = pendingMessageManager.handleSendReceipt(clientSeq, serverMsgId, String.valueOf(userSeq));

            if (success) {
                System.out.println("消息发送回执处理成功 - 客户端序列号: " + clientSeq);
            } else {
                System.out.println("消息发送回执处理失败，未找到对应的待确认消息 - 客户端序列号: " + clientSeq);
            }

        } catch (Exception e) {
            System.err.println("处理消息发送回执异常 - 客户端序列号: " + clientSeq + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 消息处理接口
     */
    public interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}