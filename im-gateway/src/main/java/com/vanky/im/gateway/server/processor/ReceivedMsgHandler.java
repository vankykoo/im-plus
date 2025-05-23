package com.vanky.im.gateway.server.processor;

import com.vanky.im.common.constant.ReceiveUserId;
import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.server.processor.client.PrivateMsgProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @create 2025/5/22 21:16
 * @description 接收消息处理器, 根据消息类型进行分发
 */
public class ReceivedMsgHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ReceivedMsgHandler.class);

    public void handle(ChatMessage chatMessage) {
        if (ReceiveUserId.SYSTEM_ID.equals(chatMessage.getToId())) {
            log.info("收到系统消息:  from:{}, type:{}",  chatMessage.getFromId(), chatMessage.getType());
            
            // 处理系统消息，根据消息类型进行分发
            if (chatMessage.getType() == ClientToServerMessageType.LOGIN_REQUEST.getValue()) {
                log.info("处理登录请求消息");
                // 登录请求处理逻辑
            } else if (chatMessage.getType() == ClientToServerMessageType.LOGOUT_REQUEST.getValue()) {
                log.info("处理登出请求消息");
                // 登出请求处理逻辑
            } else if (chatMessage.getType() == ClientToServerMessageType.HEARTBEAT.getValue()) {
                log.info("处理心跳消息");
                // 心跳消息处理逻辑
            } else {
                log.warn("未知的系统消息类型: {}", chatMessage.getType());
            }
        } else {
            log.info("收到消息:  from:{}, to:{}, type:{}",  chatMessage.getFromId(), chatMessage.getToId(), chatMessage.getType());
            // 处理用户消息，调用相应的处理器
            PrivateMsgProcessor.process(chatMessage);
        }
    }
}
