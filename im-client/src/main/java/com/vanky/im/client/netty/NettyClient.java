package com.vanky.im.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @create 2025/5/15 22:18
 * @description Netty 客户端抽象父类，用于统一管理客户端的连接和关闭，
 *              子类需要实现具体的 ChannelInitializer 来处理不同协议。
 */
public abstract class NettyClient {

    protected static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    
    protected EventLoopGroup group;
    protected Bootstrap bootstrap;
    protected Channel channel;
    protected String host;
    protected int port;
    
    /**
     * 构造函数，初始化事件循环组和引导类
     */
    public NettyClient() {
        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
    }
    
    /**
     * 构造函数，初始化事件循环组、引导类以及连接参数
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     */
    public NettyClient(String host, int port) {
        this();
        this.host = host;
        this.port = port;
    }
    
    /**
     * 初始化客户端配置
     */
    public abstract void init();
    
    /**
     * 连接到服务器
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @throws InterruptedException 如果连接过程中被中断
     */
    public abstract void connect(String host, int port) throws InterruptedException;
    
    /**
     * 使用已配置的主机和端口连接到服务器
     * 
     * @throws InterruptedException 如果连接过程中被中断
     * @throws IllegalStateException 如果主机或端口未设置
     */
    public void connect() throws InterruptedException {
        if (host == null || port <= 0) {
            throw new IllegalStateException("Host and port must be set before connecting");
        }
        connect(host, port);
    }
    
    /**
     * 发送消息到服务器
     * 
     * @param message 要发送的消息
     * @return 发送操作的ChannelFuture
     */
    public ChannelFuture sendMessage(Object message) {
        if (channel == null || !channel.isActive()) {
            logger.error("Channel is not active. Cannot send message.");
            throw new IllegalStateException("Channel is not active");
        }
        return channel.writeAndFlush(message);
    }
    
    /**
     * 关闭客户端连接，释放资源
     */
    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("Client disconnected.");
    }
    
    /**
     * 抽象方法，由子类实现，用于获取特定协议的 ChannelInitializer
     * 
     * @return ChannelInitializer<SocketChannel> Channel 初始化器
     */
    protected abstract ChannelInitializer<SocketChannel> getChannelInitializer();
    
    /**
     * 检查客户端是否已连接
     * 
     * @return 如果客户端已连接并且通道处于活动状态，则返回true
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }
    
    /**
     * 获取当前连接的主机地址
     * 
     * @return 主机地址
     */
    public String getHost() {
        return host;
    }
    
    /**
     * 设置要连接的主机地址
     * 
     * @param host 主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * 获取当前连接的端口
     * 
     * @return 端口号
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 设置要连接的端口
     * 
     * @param port 端口号
     */
    public void setPort(int port) {
        this.port = port;
    }
}
