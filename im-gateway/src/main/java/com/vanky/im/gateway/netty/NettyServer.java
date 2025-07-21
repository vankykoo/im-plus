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
        this.bossGroup = new NioEventLoopGroup(1); // BossGroup通常设置为1
        // 负责处理网络IO操作的线程组
        this.workerGroup = new NioEventLoopGroup(); // 默认线程数是 CPU核心数 * 2
        this.bootstrap = new ServerBootstrap();

        logger.info("创建NettyServer实例 - BossGroup线程数: 1, WorkerGroup线程数: {}",
                Runtime.getRuntime().availableProcessors() * 2);
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

        // 检查端口是否可用
        if (!isPortAvailable(port)) {
            logger.error("{} server cannot start: port {} is already in use", getServerType(), port);
            return;
        }

        this.port = port;
        
        new Thread(() -> {
            try {
                // 绑定端口，开始接收进来的连接
                logger.info("正在启动 {} 服务器，绑定端口: {}", getServerType(), port);
                ChannelFuture future = bootstrap.bind(port);

                // 添加连接监听器
                future.addListener(bindFuture -> {
                    if (bindFuture.isSuccess()) {
                        logger.info("{} server bind successful on port: {}", getServerType(), port);
                    } else {
                        logger.error("{} server bind failed on port: {}", getServerType(), port, bindFuture.cause());
                    }
                });

                future.sync();

                if (future.isSuccess()) {
                    serverChannel = future.channel();
                    isRunning = true;
                    logger.info("{} server started successfully on port: {}", getServerType(), port);

                    // 添加服务器Channel的监听器
                    serverChannel.pipeline().addFirst(new io.netty.channel.ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
                            logger.info("{} server接受新连接: {}", getServerType(), msg);
                            super.channelRead(ctx, msg);
                        }
                    });
                } else {
                    logger.error("{} server failed to bind port: {}, cause: {}", getServerType(), port, future.cause());
                    return;
                }

                // 等待服务器 socket 关闭
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                logger.error("{} server startup interrupted.", getServerType(), e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("{} server startup failed with exception.", getServerType(), e);
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

    /**
     * 检查端口是否可用
     * @param port 端口号
     * @return 如果端口可用返回true
     */
    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (java.io.IOException e) {
            logger.warn("Port {} is not available: {}", port, e.getMessage());
            return false;
        }
    }


}
