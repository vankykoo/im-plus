package com.vanky.im.common.protocol.codec;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProtobufMessageDecoder<T extends MessageLite> extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(ProtobufMessageDecoder.class);
    private final Parser<T> parser;

    public ProtobufMessageDecoder(Parser<T> parser) {
        this.parser = parser;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        logger.debug("Protobuf解码器收到数据 - 可读字节数: {}, Channel: {}",
                in.readableBytes(), ctx.channel().id().asShortText());

        if (in.readableBytes() < 4) {
            logger.debug("数据不足4字节，等待更多数据");
            return;
        }
        in.markReaderIndex();
        int length = in.readInt();
        logger.debug("读取消息长度: {}", length);

        if (in.readableBytes() < length) {
            logger.debug("消息不完整，期望长度: {}, 实际可读: {}", length, in.readableBytes());
            in.resetReaderIndex();
            return;
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        T msg = parser.parseFrom(bytes);
        logger.debug("成功解码Protobuf消息");
        out.add(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Protobuf解码异常 - Channel: {}, 原因: {}",
                ctx.channel().id().asShortText(), cause.getMessage(), cause);
        super.exceptionCaught(ctx, cause);
    }
} 