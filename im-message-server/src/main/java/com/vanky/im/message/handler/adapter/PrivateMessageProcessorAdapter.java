package com.vanky.im.message.handler.adapter;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.MessageProcessor;
import com.vanky.im.message.processor.PrivateMessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 私聊消息处理器适配器
 * 将现有的PrivateMessageProcessor适配到统一的MessageProcessor接口
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 私聊消息处理器适配器，实现统一消息处理接口
 */
@Slf4j
@Component
public class PrivateMessageProcessorAdapter implements MessageProcessor {
    
    @Autowired
    private PrivateMessageProcessor privateMessageProcessor;
    
    @Override
    public void process(ChatMessage chatMessage, String conversationId) throws Exception {
        // 调用原有的私聊消息处理逻辑
        privateMessageProcessor.processPrivateMessage(chatMessage, conversationId);
    }
    
    @Override
    public int[] getSupportedMessageTypes() {
        return new int[]{MessageTypeConstants.PRIVATE_CHAT_MESSAGE};
    }
    
    @Override
    public String getProcessorName() {
        return "PrivateMessageProcessor";
    }
}
