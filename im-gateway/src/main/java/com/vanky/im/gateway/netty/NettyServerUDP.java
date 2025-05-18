package com.vanky.im.gateway.netty;

import com.vanky.im.common.protocal.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocal.codec.ProtobufMessageEncoder;
import com.vanky.im.gateway.netty.udp.UdpServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vanky.im.common.protocol.ChatMessage;

/**
 * @author vanky
 * @create 2025/5/13 22:41
 * @description UDP 服务端实现。
 */
public class NettyServerUDP extends NettyServer {
    
    private static final Logger logger = LoggerFactory.getLogger(NettyServerUDP.class);
    private EventLoopGroup group;
    private Bootstrap udpBootstrap;
    
    /**
     * 构造函数，初始化UDP服务器所需的资源
     */
    public NettyServerUDP() {
        // UDP不需要使用父类的bossGroup和workerGroup，只需要一个EventLoopGroup
        this.group = new NioEventLoopGroup();
        this.udpBootstrap = new Bootstrap();
    }
    
    /**
     * 初始化UDP服务器配置
     */
    @Override
    public void init() {
        // 配置UDP服务器
        udpBootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_RCVBUF, 1024 * 1024) // 设置接收缓冲区大小
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024) // 设置发送缓冲区大小
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(new UDPChannelInitializer());
    }

    /**
     * 启动UDP服务器
     *
     * @param port 服务器监听端口
     * @throws InterruptedException 如果服务器启动过程中被中断
     */
    @Override
    public void start(int port) throws InterruptedException {
        try {
            // 绑定端口，开始接收进来的连接
            ChannelFuture future = udpBootstrap.bind(port).sync();
            logger.info("UDP server started on port: {}", port);
            
            // 等待服务器 socket 关闭
            future.channel().closeFuture().sync();
        } finally {
            // 优雅地关闭服务器
            stop();
        }
    }
    
    /**
     * 停止UDP服务器，释放资源
     */
    @Override
    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("UDP server stopped.");
    }

    /**
     * 获取UDP通道初始化器
     * 注意：此方法在UDP实现中不会被调用，仅为满足父类抽象方法要求
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return null; // UDP不使用此方法，仅为满足父类抽象方法要求
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
            // 添加UDP业务处理器
            ch.pipeline().addLast(new UdpServerHandler());
        }
    }
}
