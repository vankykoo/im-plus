package com.vanky.im.gateway.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @create 2025/5/13 22:24
 * @description Netty 服务端抽象父类，用于统一管理服务器的启动和停止，
 *              子类需要实现具体的 ChannelInitializer 来处理不同协议。
 */
public abstract class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;
    protected ServerBootstrap bootstrap;

    /**
     * 构造函数，初始化 Boss 和 Worker线程组。
     */
    public NettyServer() {
        // 负责处理客户端连接请求的线程组
        this.bossGroup = new NioEventLoopGroup(1); // 通常设置为1，因为BossGroup只负责accept事件
        // 负责处理网络IO操作的线程组
        this.workerGroup = new NioEventLoopGroup(); // 默认线程数是 CPU核心数 * 2
        this.bootstrap = new ServerBootstrap();
    }

    public abstract void init();
    public abstract void start(int port) throws InterruptedException;

    /**
     * 停止服务器，释放资源。
     */
    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("Netty server stopped.");
    }

    /**
     * 抽象方法，由子类实现，用于获取特定协议的 ChannelInitializer。
     *
     * @return ChannelInitializer<SocketChannel> Channel 初始化器
     */
    protected abstract ChannelInitializer<SocketChannel> getChannelInitializer();

}
