package com.vanky.im.gateway.netty.tcp;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.IMServiceHandler;
import com.vanky.im.gateway.session.UserChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/5/22 21:16
 * @description 接收消息处理器, 根据消息类型进行分发
 */
@ChannelHandler.Sharable
@Component
public class TcpServerHandler extends SimpleChannelInboundHandler<ChatMessage> {

    private static final Logger log = LoggerFactory.getLogger(TcpServerHandler.class);
    
    @Autowired
    private IMServiceHandler imServiceHandler;
    
    @Autowired
    private UserChannelManager userChannelManager;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        log.info("TCP接收到消息 - 类型: {}, 发送方: {}, 接收方: {}, 消息ID: {}",
                msg.getType(), msg.getFromId(), msg.getToId(), msg.getUid());

        // 委托给统一消息分发器处理
        imServiceHandler.handleMessage(msg, ctx.channel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("TCP客户端连接建立: {}, Channel ID: {}, 本地地址: {}, 当前活跃连接数: {}",
                ctx.channel().remoteAddress(), ctx.channel().id().asShortText(),
                ctx.channel().localAddress(), userChannelManager.getOnlineUserCount());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String userId = userChannelManager.getUserId(ctx.channel());
        log.info("TCP客户端连接断开: {}, Channel ID: {}, 用户ID: {}, 当前活跃连接数: {}",
                ctx.channel().remoteAddress(), ctx.channel().id().asShortText(),
                userId, userChannelManager.getOnlineUserCount());

        // 连接断开时，处理用户下线逻辑
        if (userId != null) {
            log.info("用户连接断开，执行下线处理 - 用户ID: {}", userId);
            userChannelManager.unbindChannel(userId);
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("TCP连接异常 - 远程地址: {}, 异常: {}", ctx.channel().remoteAddress(), cause.getMessage(), cause);

        // 处理用户下线逻辑
        String userId = userChannelManager.getUserId(ctx.channel());
        if (userId != null) {
            log.info("连接异常，执行用户下线处理 - 用户ID: {}", userId);
            userChannelManager.unbindChannel(userId);
        }

        ctx.close();
    }
}