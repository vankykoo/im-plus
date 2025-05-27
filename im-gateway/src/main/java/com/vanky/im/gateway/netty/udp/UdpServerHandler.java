package com.vanky.im.gateway.netty.udp;

import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.MsgGenerator;
import com.vanky.im.gateway.server.processor.server.OnlineProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpServerHandler extends SimpleChannelInboundHandler<ChatMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpServerHandler.class);
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的UDP消息
        System.out.println("[UDP] 收到消息: " + msg);
        
        // 判断是否是登录请求
        if (msg.getType() == ClientToServerMessageType.LOGIN_REQUEST.getValue()) {
            // 从消息中获取用户ID
            String userId = msg.getFromId();
            
            // 保存用户会话信息
            OnlineProcessor.getInstance().userOnline(userId, ctx.channel());
            
            System.out.println("[UDP] 用户[" + userId + "]已登录");
            
            // 发送登录成功响应（这里可以根据需求自定义响应）
            // ctx.writeAndFlush(loginResponse);
        } else if (msg.getType() == ClientToServerMessageType.LOGOUT_REQUEST.getValue()) {
            // 处理退出登录请求
            String userId = msg.getFromId();
            
            // 处理用户退出登录
            OnlineProcessor.getInstance().userOffline(userId);
            
            logger.info("[UDP] 用户 {} 已退出登录", userId);
        } else if (msg.getType() == ClientToServerMessageType.HEARTBEAT.getValue()) {
            // 处理心跳消息
            logger.info("[UDP] 处理心跳消息，来自: {}", msg.getFromId());
            
            // 回复心跳响应包
            ChatMessage heartbeatResponse = MsgGenerator.generateHeartbeatResponseMsg(msg.getFromId());
            ctx.channel().writeAndFlush(heartbeatResponse);
            logger.debug("[UDP] 已回复心跳响应: {}", heartbeatResponse.getUid());
        } else {
            // 处理其他类型消息
            // 可以根据业务需求做响应
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[UDP] 客户端连接建立: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开时，处理用户下线逻辑
        System.out.println("[UDP] 客户端连接断开: " + ctx.channel().remoteAddress());
        
        // 处理用户下线
        OnlineProcessor.getInstance().userOfflineByChannel(ctx.channel());
        
        super.channelInactive(ctx);
    }
} 