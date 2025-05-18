package com.vanky.im.gateway.netty;

import com.vanky.im.common.protocal.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocal.codec.ProtobufMessageEncoder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vanky.im.common.protocol.ChatMessage;

/**
 * @author vanky
 * @create 2025/5/13 22:42
 * @description WebSocket 服务端实现，用于处理 WebSocket 连接请求。
 */
public class NettyServerWebSocket extends NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerWebSocket.class);
    private final String websocketPath;

    public NettyServerWebSocket(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    /**
     * 初始化服务器配置
     */
    @Override
    public void init() {
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(getChannelInitializer());
    }

    /**
     * 启动服务器
     *
     * @param port 服务器监听端口
     * @throws InterruptedException 如果服务器启动过程中被中断
     */
    @Override
    public void start(int port) throws InterruptedException {
        try {
            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("WebSocket server started on port: {} with path: {}", port, websocketPath);
            future.channel().closeFuture().sync();
        } finally {
            stop();
        }
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
                // WebSocket 协议本身是基于 http 协议的，所以这边也要使用 http 解编码器
                ch.pipeline().addLast(new HttpServerCodec());
                // 以块的方式来写的处理器
                ch.pipeline().addLast(new ChunkedWriteHandler());
                // Netty 是基于分段请求的，HttpObjectAggregator 的作用是将请求分段再聚合, 参数是聚合字节的最大长度
                ch.pipeline().addLast(new HttpObjectAggregator(8192));
                // WebSocket 服务器处理的协议，用于指定给客户端连接访问的路由
                ch.pipeline().addLast(new WebSocketServerProtocolHandler(websocketPath));
                // 添加日志处理器
                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                // 添加通用Protobuf编解码器
                ch.pipeline().addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                ch.pipeline().addLast(new ProtobufMessageEncoder());
                // 添加WebSocket业务处理器
                ch.pipeline().addLast(new WebSocketServerHandler());
            }
        };
    }
}
