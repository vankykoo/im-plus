package com.vanky.im.client.netty;

import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientHandler extends SimpleChannelInboundHandler<ChatMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpClientHandler.class);
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 处理心跳响应消息
        if (msg.getType() == ClientToServerMessageType.HEARTBEAT.getValue()) {
            logger.debug("[TCP客户端] 收到心跳响应: {}", msg.getUid());
            return;
        }
        
        // 处理其他消息
        System.out.println("[TCP客户端] 收到消息: " + msg.getContent() + "，完整消息: " + msg);
    }
} 