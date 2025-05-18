package com.vanky.im.gateway.netty.websocket;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<ChatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的WebSocket消息
        System.out.println("[WebSocket] 收到消息: " + msg);
        // 可以根据业务需求做响应
    }
} 