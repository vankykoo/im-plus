package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.storage.LocalMessageStorage;
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
    
    public NettyTcpClient(String userId, String token, MessageHandler messageHandler) {
        this.userId = userId;
        this.token = token;
        this.messageHandler = messageHandler;
        this.connectLatch = new CountDownLatch(1);

        // 初始化HTTP客户端和本地存储
        this.httpClient = new HttpClient();
        this.localStorage = new LocalMessageStorage();
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

            // 委托给消息处理器
            if (messageHandler != null) {
                messageHandler.handleMessage(chatMessage);
            }
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
        
        channel.writeAndFlush(privateMsg);
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
        
        channel.writeAndFlush(groupMsg);
        System.out.println("发送群聊消息 - 从: " + userId + " 到群: " + groupId + ", 内容: " + content);
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
            // 临时解决方案：通过HTTP API查询当前用户的最大全局seq
            if (httpClient != null) {
                HttpClient.SyncCheckResponse response = httpClient.checkSyncNeeded(userId, 0L);
                if (response != null && response.isSuccess()) {
                    Long serverMaxSeq = response.getTargetSeq();
                    if (serverMaxSeq != null && serverMaxSeq > 0) {
                        localStorage.updateLastSyncSeq(userId, serverMaxSeq);
                        System.out.println("更新本地同步点 - 用户ID: " + userId + ", 新序列号: " + serverMaxSeq);
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
     * 消息处理接口
     */
    public interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}