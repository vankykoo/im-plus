package com.vanky.im.client.netty;

import com.vanky.im.common.protocol.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocol.codec.ProtobufMessageEncoder;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.MsgGenerator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.vanky.im.common.constant.ChannelOptionConstant.MAX_CONTENT_LENGTH;
import static com.vanky.im.common.constant.TimeConstant.HEARTBEAT_INTERVAL;
import static com.vanky.im.common.constant.UriConstant.DEFAULT_WEBSOCKET_PATH;
import static com.vanky.im.common.constant.UriConstant.WS_SCHEME;
import static com.vanky.im.common.constant.UriConstant.WSS_SCHEME;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description WebSocket 客户端实现，用于连接 WebSocket 服务器
 */
public class NettyClientWebSocket extends NettyClient {

    private String websocketPath;
    private boolean useSSL;
    private URI uri;
    
    // 心跳定时任务调度器
    private ScheduledExecutorService heartbeatScheduler;
    // 心跳任务的Future
    private ScheduledFuture<?> heartbeatFuture;
    // 用户ID，用于发送心跳
    private String userId;

    /**
     * 默认构造函数
     */
    public NettyClientWebSocket() {
        super();
        this.websocketPath = DEFAULT_WEBSOCKET_PATH;
        this.useSSL = false;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 带连接参数的构造函数
     *
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @param websocketPath WebSocket路径
     * @param useSSL 是否使用SSL
     */
    public NettyClientWebSocket(String host, int port, String websocketPath, boolean useSSL) {
        super(host, port);
        this.websocketPath = websocketPath;
        this.useSSL = useSSL;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 初始化WebSocket客户端配置
     */
    @Override
    public void init() {
        try {
            // 构建WebSocket URI
            String scheme = useSSL ? WSS_SCHEME : WS_SCHEME;
            uri = new URI(scheme + "://" + host + ":" + port + websocketPath);
            
            // 配置WebSocket客户端
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(getChannelInitializer());
        } catch (URISyntaxException e) {
            logger.error("Invalid WebSocket URI", e);
            throw new IllegalArgumentException("Invalid WebSocket URI", e);
        }
    }

    /**
     * 连接到WebSocket服务器
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
            // 连接到服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            logger.info("Connected to WebSocket server at {}:{}{}", host, port, websocketPath);
            
            // 等待连接关闭
            // channel.closeFuture().sync(); // 注释掉以避免阻塞当前线程
        } catch (InterruptedException e) {
            logger.error("Failed to connect to WebSocket server at {}:{}{}", host, port, websocketPath, e);
            disconnect();
            throw e;
        }
    }

    /**
     * 获取WebSocket通道初始化器
     *
     * @return ChannelInitializer WebSocket Channel 初始化器
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 如果需要SSL，添加SSL处理器
                if (useSSL) {
                    SslContext sslContext = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                    ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port));
                }
                
                // WebSocket是基于HTTP协议的，所以需要HTTP编解码器
                ch.pipeline().addLast(new HttpClientCodec());
                // 聚合HTTP消息
                ch.pipeline().addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                // WebSocket客户端协议处理器
                ch.pipeline().addLast(new WebSocketClientProtocolHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                                uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders())));
                // 添加日志处理器
                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                // 添加通用Protobuf编解码器
                ch.pipeline().addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                ch.pipeline().addLast(new ProtobufMessageEncoder());
                // 添加WebSocket客户端业务处理器
                ch.pipeline().addLast(new WebSocketClientHandler());
            }
        };
    }

    /**
     * 获取WebSocket路径
     *
     * @return WebSocket路径
     */
    public String getWebsocketPath() {
        return websocketPath;
    }

    /**
     * 设置WebSocket路径
     *
     * @param websocketPath WebSocket路径
     */
    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    /**
     * 检查是否使用SSL
     *
     * @return 如果使用SSL则返回true
     */
    public boolean isUseSSL() {
        return useSSL;
    }

    /**
     * 设置是否使用SSL
     *
     * @param useSSL 是否使用SSL
     */
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
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
        
        logger.info("启动WebSocket心跳定时任务，间隔: {}秒", HEARTBEAT_INTERVAL);
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                try {
                    // 发送心跳消息
                    ChatMessage heartbeatMsg = MsgGenerator.generateHeartbeatMsg(userId);
                    sendMessage(heartbeatMsg);
                    logger.debug("发送WebSocket心跳包: {}", heartbeatMsg.getUid());
                } catch (Exception e) {
                    logger.error("发送WebSocket心跳包异常", e);
                }
            } else {
                logger.warn("WebSocket连接已断开，无法发送心跳");
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
            logger.info("WebSocket心跳定时任务已停止");
        }
    }
    
    @Override
    public void disconnect() {
        stopHeartbeat();
        super.disconnect();
    }
}