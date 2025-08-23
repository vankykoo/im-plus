package com.vanky.im.gateway.netty;

import com.vanky.im.common.protocol.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocol.codec.ProtobufMessageEncoder;
import com.vanky.im.gateway.netty.handler.CommonHeartbeatHandler;
import com.vanky.im.gateway.netty.websocket.WebSocketFrameEncoder;
import com.vanky.im.gateway.netty.handler.HttpAuthHandler;
import com.vanky.im.gateway.netty.websocket.WebSocketServerHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vanky.im.common.protocol.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.vanky.im.common.constant.ChannelOptionConstant.MAX_CONTENT_LENGTH;
import static com.vanky.im.common.constant.ChannelOptionConstant.SO_BACKLOG;
import static com.vanky.im.common.constant.CommonConstant.WEBSOCKET_PROTOCOL;
import static com.vanky.im.common.constant.TimeConstant.IDLE_TIME_DISABLE;
import static com.vanky.im.common.constant.TimeConstant.SERVER_READ_IDLE_TIMEOUT;

/**
 * @author vanky
 * @create 2025/5/13 22:42
 * @description WebSocket 服务端实现，用于处理 WebSocket 连接请求。
 */
@Component
public class NettyServerWebSocket extends NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerWebSocket.class);
    
    @Value("${netty.server.websocket.path:/}")
    private String websocketPath;
    
    @Autowired
    private WebSocketServerHandler webSocketServerHandler;

    @Autowired
    private WebSocketFrameEncoder webSocketFrameEncoder;

    @Autowired
    private HttpAuthHandler httpAuthHandler;

    public NettyServerWebSocket() {
        // Constructor is now empty as websocketPath is injected via @Value
    }

    /**
     * 初始化服务器配置
     */
    @Override
    public void init() {
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, SO_BACKLOG)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(getChannelInitializer());
    }

    /**
     * 启动服务器
     *
     * @param port 服务器监听端口
     */
    @Override
    public void start(int port) {
        if (isRunning) {
            logger.warn("WebSocket server is already running on port: {}", this.port);
            return;
        }
        
        this.port = port;
        
        new Thread(() -> {
            try {
                // 绑定端口，开始接收进来的连接
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();
                isRunning = true;
                logger.info("WebSocket server started on port: {} with path: {}", port, websocketPath);
                
                // 等待服务器 socket 关闭
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                logger.error("WebSocket server startup failed.", e);
                Thread.currentThread().interrupt();
            } finally {
                // 优雅地关闭服务器
                stop();
            }
        }, "WebSocket-Server-Thread").start();
    }

    /**
     * 获取 Channel 初始化器
     *
     * @return ChannelInitializer WebSocket Channel 初始化器
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 添加日志处理器（放在最前面，记录所有事件）
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                // WebSocket 协议本身是基于 http 协议的，所以这边也要使用 http 解编码器
                ch.pipeline().addLast(new HttpServerCodec());
                // 以块的方式来写的处理器
                ch.pipeline().addLast(new ChunkedWriteHandler());
                // Netty 是基于分段请求的，HttpObjectAggregator 的作用是将请求分段再聚合, 参数是聚合字节的最大长度
                ch.pipeline().addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                // 添加 HTTP 认证处理器
                ch.pipeline().addLast(httpAuthHandler);
                // WebSocket 服务器处理的协议，用于指定给客户端连接访问的路由
                ch.pipeline().addLast(new WebSocketServerProtocolHandler(websocketPath, "chat", true));
                // 添加空闲状态处理器，设置读空闲超时时间
                ch.pipeline().addLast(new IdleStateHandler(SERVER_READ_IDLE_TIMEOUT, IDLE_TIME_DISABLE, IDLE_TIME_DISABLE, TimeUnit.SECONDS));
                // 添加通用心跳处理器
                ch.pipeline().addLast(new CommonHeartbeatHandler(WEBSOCKET_PROTOCOL));
                // 添加自定义的编码器，用于将ChatMessage编码为BinaryWebSocketFrame
                ch.pipeline().addLast(webSocketFrameEncoder);
                // WebSocket处理器直接处理BinaryWebSocketFrame，不再需要通用Protobuf编解码器 - 使用Spring管理的实例
                ch.pipeline().addLast(webSocketServerHandler);
            }
        };
    }
    
    /**
     * 获取服务器类型
     * 
     * @return 服务器类型名称
     */
    @Override
    protected String getServerType() {
        return "WebSocket";
    }
}
