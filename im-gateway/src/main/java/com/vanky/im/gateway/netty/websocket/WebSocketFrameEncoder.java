package com.vanky.im.gateway.netty.websocket;

import com.vanky.im.common.protocol.ChatMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author vanky
 * @description 将 ChatMessage 对象编码为 BinaryWebSocketFrame
 */
@Component
@ChannelHandler.Sharable
public class WebSocketFrameEncoder extends MessageToMessageEncoder<ChatMessage> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ChatMessage msg, List<Object> out) throws Exception {
        try {
            // 将 ChatMessage 转换为 byte[]
            byte[] bytes = msg.toByteArray();
            // 将 byte[] 包装成 BinaryWebSocketFrame
            BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes));
            // 将 frame 添加到输出列表，传递给下一个 handler
            out.add(frame);
            logger.debug("ChatMessage编码为BinaryWebSocketFrame成功 - Type: {}, To: {}, Channel: {}",
                    msg.getType(), msg.getToId(), ctx.channel().id().asShortText());
        } catch (Exception e) {
            logger.error("ChatMessage编码为BinaryWebSocketFrame失败 - Channel: {}", ctx.channel().id().asShortText(), e);
        }
    }
}