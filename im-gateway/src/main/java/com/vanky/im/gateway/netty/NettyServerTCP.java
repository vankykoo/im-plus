package com.vanky.im.gateway.netty;

import com.vanky.im.gateway.netty.handler.CommonHeartbeatHandler;
import com.vanky.im.gateway.netty.tcp.TcpServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocol.codec.ProtobufMessageEncoder;

import java.util.concurrent.TimeUnit;

import static com.vanky.im.common.constant.ChannelOptionConstant.SO_BACKLOG;
import static com.vanky.im.common.constant.CommonConstant.TCP_PROTOCOL;
import static com.vanky.im.common.constant.TimeConstant.IDLE_TIME_DISABLE;
import static com.vanky.im.common.constant.TimeConstant.SERVER_READ_IDLE_TIMEOUT;

/**
 * @author vanky
 * @create 2025/5/13 22:38
 * @description TCP 服务端实现，用于处理 TCP 连接请求
 */
@Component
public class NettyServerTCP extends NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerTCP.class);
    
    @Autowired
    private TcpServerHandler tcpServerHandler;

    /**
     * 初始化服务器配置
     */
    @Override
    public void init() {
        // 配置服务器启动引导类
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, SO_BACKLOG)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(getChannelInitializer());
    }

    /**
     * 获取服务器类型
     * 
     * @return 服务器类型名称
     */
    @Override
    protected String getServerType() {
        return "TCP";
    }

    /**
     * 获取 Channel 初始化器
     *
     * @return ChannelInitializer TCP Channel 初始化器
     */
    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 添加日志处理器（放在最前面，记录所有事件）
                ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                // 添加空闲状态处理器，设置读空闲超时时间
                ch.pipeline().addLast(new IdleStateHandler(SERVER_READ_IDLE_TIMEOUT, IDLE_TIME_DISABLE, IDLE_TIME_DISABLE, TimeUnit.SECONDS));
                // 添加通用心跳处理器
                ch.pipeline().addLast(new CommonHeartbeatHandler(TCP_PROTOCOL));
                // 添加通用Protobuf编解码器
                ch.pipeline().addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                ch.pipeline().addLast(new ProtobufMessageEncoder());
                // 添加业务处理器 - 使用Spring管理的实例
                ch.pipeline().addLast(tcpServerHandler);
            }
        };
    }
}
