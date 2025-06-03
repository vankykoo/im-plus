package com.vanky.im.common.protocol.codec;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ProtobufMessageDecoder<T extends MessageLite> extends ByteToMessageDecoder {
    private final Parser<T> parser;

    public ProtobufMessageDecoder(Parser<T> parser) {
        this.parser = parser;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        int length = in.readInt();
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        T msg = parser.parseFrom(bytes);
        out.add(msg);
    }
} 