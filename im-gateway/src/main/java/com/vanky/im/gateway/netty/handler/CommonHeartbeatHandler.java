package com.vanky.im.gateway.netty.handler;

import com.vanky.im.gateway.server.processor.server.OnlineProcessor;
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
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲，认为客户端连接可能已失效
                String userId = OnlineProcessor.getInstance().getUserIdByChannel(ctx.channel());
                logger.warn("[{}] 检测到读空闲，关闭连接。Channel: {}, 用户ID: {}", 
                        protocolName, ctx.channel().id().asLongText(), userId != null ? userId : "未登录");
                
                // 关闭连接
                ctx.channel().close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
} 