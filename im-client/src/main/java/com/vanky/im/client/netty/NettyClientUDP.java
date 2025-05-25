package com.vanky.im.client.netty;

import com.vanky.im.common.protocal.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocal.codec.ProtobufMessageEncoder;
import com.vanky.im.common.util.MsgGenerator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vanky.im.common.protocol.ChatMessage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.vanky.im.common.constant.ChannelOptionConstant.SO_RCVBUF;
import static com.vanky.im.common.constant.ChannelOptionConstant.SO_SNDBUF;
import static com.vanky.im.common.constant.TimeConstant.HEARTBEAT_INTERVAL;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description UDP 客户端实现，用于连接 UDP 服务器
 */
public class NettyClientUDP extends NettyClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClientUDP.class);

    // 心跳定时任务调度器
    private ScheduledExecutorService heartbeatScheduler;
    // 心跳任务的Future
    private ScheduledFuture<?> heartbeatFuture;
    // 用户ID，用于发送心跳
    private String userId;

    /**
     * 默认构造函数
     */
    public NettyClientUDP() {
        super();
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 带连接参数的构造函数
     *
     * @param host 服务器主机地址
     * @param port 服务器端口
     */
    public NettyClientUDP(String host, int port) {
        super(host, port);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 初始化UDP客户端配置
     */
    @Override
    public void init() {
        // 配置UDP客户端
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_RCVBUF, SO_RCVBUF) // 设置接收缓冲区大小
                .option(ChannelOption.SO_SNDBUF, SO_SNDBUF) // 设置发送缓冲区大小
                .handler(new UDPChannelInitializer());
    }

    /**
     * 连接到UDP服务器
     * 注意：UDP是无连接的，这里的"连接"实际上是绑定本地端口并设置默认的远程地址
     *
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @throws InterruptedException 如果连接过程中被中断
     */
    @Override
    public void connect(String host, int port) throws InterruptedException {
        this.host = host;
        this.port = port;
        
        // 确保客户端已初始化
        if (bootstrap.config().group() == null) {
            init();
        }
        
        try {
            // 绑定任意可用端口
            ChannelFuture future = bootstrap.bind(0).sync();
            channel = future.channel();
            logger.info("UDP client ready to communicate with server at {}:{}", host, port);
            
            // 注意：UDP是无连接的，不需要等待连接关闭
            // channel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Failed to initialize UDP client for server at {}:{}", host, port, e);
            disconnect();
            throw e;
        }
    }

    /**
     * 发送UDP消息到服务器
     * 由于UDP的特性，需要为每个消息指定目标地址
     *
     * @param message 要发送的消息
     * @return 发送操作的ChannelFuture
     */
    @Override
    public ChannelFuture sendMessage(Object message) {
        if (channel == null || !channel.isActive()) {
            logger.error("Channel is not active. Cannot send message.");
            throw new IllegalStateException("Channel is not active");
        }
        
        // 对于UDP，需要使用DatagramPacket包装消息并指定目标地址
        // 这里假设message已经是正确格式的DatagramPacket
        // 如果不是，子类应该重写此方法进行适当的转换
        return channel.writeAndFlush(message);
    }

    /**
     * 获取UDP通道初始化器
     * 注意：此方法在UDP实现中不会被调用，仅为满足父类抽象方法要求
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        // UDP不使用SocketChannel，但为了满足父类抽象方法要求，返回null
        return null;
    }
    
    /**
     * 设置用户ID，用于发送心跳
     * @param userId 用户ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * 启动心跳定时任务
     */
    public void startHeartbeat() {
        if (userId == null || userId.isEmpty()) {
            logger.warn("无法启动心跳，用户ID未设置");
            return;
        }
        
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            logger.info("心跳定时任务已经在运行中");
            return;
        }
        
        logger.info("启动UDP心跳定时任务，间隔: {}秒", HEARTBEAT_INTERVAL);
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                try {
                    // 发送心跳消息
                    ChatMessage heartbeatMsg = MsgGenerator.generateHeartbeatMsg(userId);
                    sendMessage(heartbeatMsg);
                    logger.debug("发送UDP心跳包: {}", heartbeatMsg.getUid());
                } catch (Exception e) {
                    logger.error("发送UDP心跳包异常", e);
                }
            } else {
                logger.warn("UDP连接已断开，无法发送心跳");
                stopHeartbeat();
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * 停止心跳定时任务
     */
    public void stopHeartbeat() {
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(true);
            logger.info("UDP心跳定时任务已停止");
        }
    }
    
    @Override
    public void disconnect() {
        stopHeartbeat();
        super.disconnect();
    }
    
    /**
     * UDP通道初始化器，用于配置UDP通道的处理器
     */
    private class UDPChannelInitializer extends ChannelInitializer<DatagramChannel> {
        @Override
        protected void initChannel(DatagramChannel ch) throws Exception {
            // 添加日志处理器
            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
            // 添加通用Protobuf编解码器
            ch.pipeline().addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
            ch.pipeline().addLast(new ProtobufMessageEncoder());
            // 添加UDP客户端业务处理器
            ch.pipeline().addLast(new UdpClientHandler());
        }
    }
}