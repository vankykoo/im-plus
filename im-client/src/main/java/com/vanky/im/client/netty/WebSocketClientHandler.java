package com.vanky.im.client.netty;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class WebSocketClientHandler extends SimpleChannelInboundHandler<ChatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的WebSocket消息
        System.out.println("[WebSocket客户端] 收到消息: " + msg);
    }
} 