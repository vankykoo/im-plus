package com.vanky.im.gateway.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @create 2025/5/25 16:30
 * @description 通用心跳处理器，适用于所有协议类型
 */
public class CommonHeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommonHeartbeatHandler.class);
    private final String protocolName;  // 协议名称，用于日志

    public CommonHeartbeatHandler(String protocolName) {
        this.protocolName = protocolName;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("[{}] 心跳处理器 - 连接激活: {}", protocolName, ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("[{}] 心跳处理器 - 连接断开: {}", protocolName, ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("[{}] 心跳处理器 - 连接异常: {}, 原因: {}",
                protocolName, ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                logger.warn("[{}] 检测到读空闲超时，关闭连接 - 远程地址: {}, Channel: {}",
                        protocolName, ctx.channel().remoteAddress(), ctx.channel().id().asShortText());
                // 关闭连接
                ctx.channel().close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                logger.debug("[{}] 检测到写空闲 - 远程地址: {}", protocolName, ctx.channel().remoteAddress());
            } else if (event.state() == IdleState.ALL_IDLE) {
                logger.debug("[{}] 检测到读写空闲 - 远程地址: {}", protocolName, ctx.channel().remoteAddress());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
} 