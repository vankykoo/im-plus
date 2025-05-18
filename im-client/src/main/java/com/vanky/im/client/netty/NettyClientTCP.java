package com.vanky.im.client.netty;

import com.vanky.im.common.protocal.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocal.codec.ProtobufMessageEncoder;
import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description TCP 客户端实现，用于连接 TCP 服务器
 */
public class NettyClientTCP extends NettyClient {

    /**
     * 默认构造函数
     */
    public NettyClientTCP() {
        super();
    }

    /**
     * 带连接参数的构造函数
     *
     * @param host 服务器主机地址
     * @param port 服务器端口
     */
    public NettyClientTCP(String host, int port) {
        super(host, port);
    }

    /**
     * 初始化TCP客户端配置
     */
    @Override
    public void init() {
        // 配置客户端
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(getChannelInitializer());
    }

    /**
     * 连接到TCP服务器
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
            logger.info("Connected to TCP server at {}:{}", host, port);
            
            // 等待连接关闭
            // channel.closeFuture().sync(); // 注释掉以避免阻塞当前线程
        } catch (InterruptedException e) {
            logger.error("Failed to connect to TCP server at {}:{}", host, port, e);
            disconnect();
            throw e;
        }
    }

    /**
     * 获取TCP通道初始化器
     *
     * @return ChannelInitializer TCP Channel 初始化器
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 添加日志处理器
                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                // 添加通用Protobuf编解码器
                ch.pipeline().addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                ch.pipeline().addLast(new ProtobufMessageEncoder());
                // 添加TCP客户端业务处理器
                ch.pipeline().addLast(new TcpClientHandler());
            }
        };
    }
}