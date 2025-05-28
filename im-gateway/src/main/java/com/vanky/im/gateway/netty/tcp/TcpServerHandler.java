package com.vanky.im.gateway.netty.tcp;

import com.vanky.im.common.constant.ReceiveUserId;
import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.MsgGenerator;
import com.vanky.im.common.util.TokenUtil;
import com.vanky.im.gateway.server.processor.server.OnlineProcessor;
import com.vanky.im.gateway.server.processor.client.PrivateMsgProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @create 2025/5/22 21:16
 * @description 接收消息处理器, 根据消息类型进行分发
 */
public class TcpServerHandler extends SimpleChannelInboundHandler<ChatMessage> {

    private static final Logger log = LoggerFactory.getLogger(TcpServerHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatMessage msg) throws Exception {
        // 这里可以处理收到的消息
        System.out.println("收到消息内容: " + msg.getContent() + "，完整消息: " + msg);

        if (ReceiveUserId.SYSTEM_ID.equals(msg.getToId())) {
            log.info("收到【系统】消息:  发送方ID:{}, 消息类型:{}", msg.getFromId(), ClientToServerMessageType.getLabelByValue(msg.getType()));

            // 处理系统消息，根据消息类型进行分发
            if (msg.getType() == ClientToServerMessageType.LOGIN_REQUEST.getValue()) {
                log.info("处理登录请求消息");
                // 登录请求处理逻辑
                // 从消息中获取用户ID和token
                String userId = msg.getFromId();
                String token = msg.getToken();
                
                // 验证token
                if (token == null || token.isEmpty()) {
                    log.warn("用户 {} 登录失败: token为空", userId);
                    ctx.channel().close();
                    return;
                }

                // 暂时不做token校验
                //String validUserId = TokenUtil.verifyToken(token);
                //if (validUserId == null || !validUserId.equals(userId)) {
                //    log.warn("用户 {} 登录失败: token无效", userId);
                //    ctx.channel().close();
                //    return;
                //}
                
                log.info("用户 {} token验证成功", userId);

                // 保存用户会话信息
                OnlineProcessor.getInstance().userOnline(userId, ctx.channel());
                // 发送登录成功响应（这里可以根据需求自定义响应）
                // ctx.writeAndFlush(loginResponse);
            } else if (msg.getType() == ClientToServerMessageType.LOGOUT_REQUEST.getValue()) {
                log.info("处理退出登录请求消息");
                // 退出登录请求处理逻辑
                String userId = msg.getFromId();
                
                // 处理用户退出登录
                OnlineProcessor.getInstance().userOffline(userId);
                
                // 可以在这里发送退出成功的响应消息
                log.info("用户 {} 已退出登录", userId);
            } else if (msg.getType() == ClientToServerMessageType.HEARTBEAT.getValue()) {
                log.info("处理心跳消息，来自: {}", msg.getFromId());
                // 心跳消息处理逻辑
                // 可以选择回复心跳响应包
                ChatMessage heartbeatResponse = MsgGenerator.generateHeartbeatResponseMsg(msg.getFromId());
                ctx.channel().writeAndFlush(heartbeatResponse);
                log.debug("已回复心跳响应: {}", heartbeatResponse.getUid());
            } else {
                log.warn("未知的系统消息类型: {}", msg.getType());
            }
        } else {
            log.info("收到 [C2C] 消息:  from:{}, to:{}, type:{}", msg.getFromId(), msg.getToId(), ClientToClientMessageType.getLabelByValue(msg.getType()));
            // 处理用户消息，调用相应的处理器
            PrivateMsgProcessor.process(msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("TCP客户端连接建立: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开时，可以在这里处理用户下线逻辑
        System.out.println("TCP客户端连接断开: " + ctx.channel().remoteAddress());

        // 处理用户下线
        OnlineProcessor.getInstance().userOfflineByChannel(ctx.channel());

        super.channelInactive(ctx);
    }
}