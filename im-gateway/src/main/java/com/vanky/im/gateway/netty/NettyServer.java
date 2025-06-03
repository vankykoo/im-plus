package com.vanky.im.gateway.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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
    protected Channel serverChannel;
    protected int port;
    protected volatile boolean isRunning = false;

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

    /**
     * 初始化服务器配置
     */
    @PostConstruct
    public abstract void init();

    /**
     * 启动服务器
     *
     * @param port 服务器监听端口
     */
    public void start(int port) {
        if (isRunning) {
            logger.warn("Server is already running on port: {}", this.port);
            return;
        }
        
        this.port = port;
        
        new Thread(() -> {
            try {
                // 绑定端口，开始接收进来的连接
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();
                isRunning = true;
                logger.info("{} server started on port: {}", getServerType(), port);
                
                // 等待服务器 socket 关闭
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                logger.error("{} server startup failed.", getServerType(), e);
                Thread.currentThread().interrupt();
            } finally {
                // 优雅地关闭服务器
                stop();
            }
        }, getServerType() + "-Server-Thread").start();
    }

    /**
     * 停止服务器，释放资源。
     */
    @PreDestroy
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("{} server stopped.", getServerType());
    }

    /**
     * 获取服务器类型
     * 
     * @return 服务器类型名称
     */
    protected abstract String getServerType();

    /**
     * 抽象方法，由子类实现，用于获取特定协议的 ChannelInitializer。
     *
     * @return ChannelInitializer<SocketChannel> Channel 初始化器
     */
    protected abstract ChannelInitializer<SocketChannel> getChannelInitializer();
    
    /**
     * 判断服务器是否正在运行
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取服务器端口
     * 
     * @return 服务器端口
     */
    public int getPort() {
        return port;
    }


}
