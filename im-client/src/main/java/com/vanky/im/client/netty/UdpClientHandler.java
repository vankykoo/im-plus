package com.vanky.im.client.netty;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class UdpClientHandler extends SimpleChannelInboundHandler<ChatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的UDP消息
        System.out.println("[UDP客户端] 收到消息: " + msg);
    }
} 