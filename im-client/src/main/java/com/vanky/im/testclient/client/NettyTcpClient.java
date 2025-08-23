package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.codec.ProtobufMessageDecoder;
import com.vanky.im.common.protocol.codec.ProtobufMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Netty 的 TCP 客户端实现。
 *
 * @author vanky
 * @since 2025-08-23
 */
public class NettyTcpClient extends AbstractClient {

    private static final String GATEWAY_HOST = ClientConfig.getProperty("server.base.ip", "localhost");
    private static final int GATEWAY_PORT = Integer.parseInt(ClientConfig.getProperty("tcp.port", "8900"));

    private EventLoopGroup group;
    private Channel channel;

    public NettyTcpClient(String userId, String token, MessageHandler messageHandler) {
        super(userId, token, messageHandler);
    }

    @Override
    protected void doConnect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // 注意：心跳逻辑已移至 AbstractClient，但 IdleStateHandler 仍在此处用于触发事件
                            pipeline.addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
                            pipeline.addLast(new ProtobufMessageDecoder<>(ChatMessage.parser()));
                            pipeline.addLast(new ProtobufMessageEncoder());
                            pipeline.addLast(new TcpClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(GATEWAY_HOST, GATEWAY_PORT).sync();
            channel = future.channel();

        } catch (Exception e) {
            System.err.println("TCP 连接失败 - 用户: " + userId + " - " + e.getMessage());
            onDisconnected(); // 连接失败时触发重连
        }
    }

    @Override
    protected void doDisconnect() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Override
    protected void sendMessageInternal(ChatMessage message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message).addListener(future -> {
                if (!future.isSuccess()) {
                    System.err.println("消息发送失败 - 类型: " + message.getType() + ", 原因: " + future.cause().getMessage());
                }
            });
        } else {
            System.err.println("Channel 不可用，无法发送消息 - 类型: " + message.getType());
        }
    }

    /**
     * Netty 的 Channel Handler，负责处理网络事件。
     */
    private class TcpClientHandler extends SimpleChannelInboundHandler<ChatMessage> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 连接激活后，调用基类的 onConnected 方法
            onConnected();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ChatMessage chatMessage) {
            // 收到消息后，交由基类的 onMessageReceived 方法统一处理
            onMessageReceived(chatMessage);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // 连接断开后，调用基类的 onDisconnected 方法
            onDisconnected();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("TCP 连接异常 - 用户: " + userId + " - " + cause.getMessage());
            ctx.close(); // 发生异常时关闭连接，会触发 channelInactive
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            // IdleStateHandler 触发的事件，但心跳发送逻辑已在基类中处理
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    // 心跳由基类的 ScheduledExecutorService 统一发送，此处仅作日志记录
                    // System.out.println("Netty IdleStateHandler 触发写空闲事件");
                }
            }
            super.userEventTriggered(ctx, evt);
        }
    }
}