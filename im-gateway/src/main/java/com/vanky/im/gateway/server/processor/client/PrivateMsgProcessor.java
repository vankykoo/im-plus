package com.vanky.im.gateway.server.processor.client;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.session.MsgSender;

/**
 * @author vanky
 * @create 2025/5/22 21:07
 * @description 私聊消息处理器
 */
public class PrivateMsgProcessor {

    public static void process(ChatMessage msg) {
        MsgSender.sendMsg(msg.getToId(), msg);
    }
}
