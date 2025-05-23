package com.vanky.im.gateway.session;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.OnlineProcessor;
import io.netty.channel.Channel;

/**
 * @author vanky
 * @create 2025/5/22 21:42
 * @description 消息发送者, 封装了发送消息的方法
 */
public class MsgSender {

    public static void sendMsg(String userId, ChatMessage chatMessage)
    {
        Channel channel = OnlineProcessor.getInstance().getUserChannel(userId);
        if (channel != null)
        {
            channel.writeAndFlush(chatMessage);
        }
    }

}
