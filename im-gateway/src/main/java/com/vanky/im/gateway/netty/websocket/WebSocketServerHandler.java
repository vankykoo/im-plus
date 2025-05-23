package com.vanky.im.gateway.netty.websocket;

import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.OnlineProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<ChatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的WebSocket消息
        System.out.println("[WebSocket] 收到消息: " + msg);
        
        // 判断是否是登录请求
        if (msg.getType() == ClientToServerMessageType.LOGIN_REQUEST.getValue()) {
            // 从消息中获取用户ID
            String userId = msg.getFromId();

            // 保存用户会话信息
            OnlineProcessor.getInstance().userOnline(userId, ctx.channel());
            
            System.out.println("[WebSocket] 用户[" + userId + "]已登录");
            // 发送登录成功响应（这里可以根据需求自定义响应）
            // ctx.writeAndFlush(loginResponse);
        } else {
            // 处理其他类型消息
            // 可以根据业务需求做响应
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[WebSocket] 客户端连接建立: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开时，处理用户下线逻辑
        System.out.println("[WebSocket] 客户端连接断开: " + ctx.channel().remoteAddress());
        
        // 处理用户下线
        OnlineProcessor.getInstance().userOfflineByChannel(ctx.channel());
        
        super.channelInactive(ctx);
    }
} 