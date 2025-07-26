package com.vanky.im.gateway.netty.websocket;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.IMServiceHandler;
import com.vanky.im.gateway.session.UserChannelManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author vanky
 * @create 2025/5/22 21:16
 * @description WebSocket消息处理器
 */
@Component
public class WebSocketServerHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);
    
    @Autowired
    private IMServiceHandler imServiceHandler;
    
    @Autowired
    private UserChannelManager userChannelManager;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) throws Exception {
        logger.debug("WebSocket收到二进制帧 - Channel: {}", ctx.channel().id().asShortText());

        // 获取二进制数据
        ByteBuf content = frame.content();

        // 获取二进制数据的字节长度
        int length = content.readableBytes();

        if (length == 0) {
            logger.warn("WebSocket收到空消息 - Channel: {}", ctx.channel().id().asShortText());
            return;
        }

        logger.debug("WebSocket消息长度: {} bytes - Channel: {}", length, ctx.channel().id().asShortText());

        // 解析消息
        try {
            // 直接读取所有字节数据（客户端发送的是纯Protobuf数据，没有长度前缀）
            byte[] msgBytes = new byte[length];
            content.readBytes(msgBytes);

            // 解析成ChatMessage对象
            ChatMessage msg = ChatMessage.parseFrom(msgBytes);

            logger.info("WebSocket接收到消息 - 类型: {}, 发送方: {}, 接收方: {}, 消息ID: {}, Channel: {}",
                    msg.getType(), msg.getFromId(), msg.getToId(), msg.getUid(), ctx.channel().id().asShortText());

            // 委托给统一消息分发器处理
            imServiceHandler.handleMessage(msg, ctx.channel());
        } catch (Exception e) {
            logger.error("WebSocket消息解析异常 - Channel: {}, 消息长度: {}",
                    ctx.channel().id().asShortText(), length, e);
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("[WebSocket] 客户端连接建立: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("[WebSocket] 客户端连接断开: {}", ctx.channel().remoteAddress());
        
        // 连接断开时，处理用户下线逻辑
        String userId = userChannelManager.getUserId(ctx.channel());
        if (userId != null) {
            logger.info("用户连接断开，执行下线处理 - 用户ID: {}", userId);
            userChannelManager.unbindChannel(userId);
        }
        
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocket连接异常 - 远程地址: {}, 异常: {}", ctx.channel().remoteAddress(), cause.getMessage(), cause);

        // 处理用户下线逻辑
        String userId = userChannelManager.getUserId(ctx.channel());
        if (userId != null) {
            logger.info("连接异常，执行用户下线处理 - 用户ID: {}", userId);
            userChannelManager.unbindChannel(userId);
        }

        ctx.close();
    }
}