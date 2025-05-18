package com.vanky.im.client.netty;

import com.vanky.im.common.protocal.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocal.codec.ProtobufMessageEncoder;
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

import com.vanky.im.common.protocol.ChatMessage;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description WebSocket 客户端实现，用于连接 WebSocket 服务器
 */
public class NettyClientWebSocket extends NettyClient {

    private String websocketPath;
    private boolean useSSL;
    private URI uri;

    /**
     * 默认构造函数
     */
    public NettyClientWebSocket() {
        super();
        this.websocketPath = "/";
        this.useSSL = false;
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
    }

    /**
     * 初始化WebSocket客户端配置
     */
    @Override
    public void init() {
        try {
            // 构建WebSocket URI
            String scheme = useSSL ? "wss" : "ws";
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
                ch.pipeline().addLast(new HttpObjectAggregator(8192));
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
}