package com.vanky.im.gateway.netty.udp;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.IMServiceHandler;
import com.vanky.im.gateway.session.UserChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/5/22 21:17
 * @description UDP服务器消息处理器
 */
@Component
public class UdpServerHandler extends SimpleChannelInboundHandler<ChatMessage> {

    private static final Logger logger = LoggerFactory.getLogger(UdpServerHandler.class);
    
    @Autowired
    private IMServiceHandler imServiceHandler;
    
    @Autowired
    private UserChannelManager userChannelManager;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        logger.debug("UDP接收到消息 - 类型: {}, 发送方: {}, 接收方: {}, 消息ID: {}", 
                msg.getType(), msg.getFromId(), msg.getToId(), msg.getUid());
        
        // 委托给统一消息分发器处理
        imServiceHandler.handleMessage(msg, ctx.channel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("UDP客户端连接建立: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("UDP客户端连接断开: {}", ctx.channel().remoteAddress());
        
        // 连接断开时，处理用户下线逻辑
        String userId = userChannelManager.getUserId(ctx.channel());
        if (userId != null) {
            logger.info("用户连接断开，执行下线处理 - 用户ID: {}", userId);
            userChannelManager.unbindChannel(userId);
        }

        super.channelInactive(ctx);
    }
}