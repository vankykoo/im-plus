package com.vanky.im.message.handler.adapter;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.MessageProcessor;
import com.vanky.im.message.processor.MessageAckProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ACK消息处理器适配器
 * 将现有的MessageAckProcessor适配到统一的MessageProcessor接口
 * 
 * @author vanky
 * @create 2025-08-09
 * @description ACK消息处理器适配器，实现统一消息处理接口
 */
@Slf4j
@Component
public class MessageAckProcessorAdapter implements MessageProcessor {
    
    @Autowired
    private MessageAckProcessor messageAckProcessor;
    
    @Override
    public void process(ChatMessage chatMessage, String conversationId) throws Exception {
        int messageType = chatMessage.getType();
        
        // 根据ACK消息类型调用对应的处理方法
        if (messageType == MessageTypeConstants.MESSAGE_ACK) {
            // 处理单个消息确认
            messageAckProcessor.processMessageAck(chatMessage);
        } else if (messageType == MessageTypeConstants.BATCH_MESSAGE_ACK) {
            // 处理批量消息确认
            messageAckProcessor.processBatchMessageAck(chatMessage);
        } else if (messageType == MessageTypeConstants.GROUP_CONVERSATION_ACK) {
            // 处理群聊会话ACK确认
            messageAckProcessor.processGroupConversationAck(chatMessage);
        } else {
            throw new IllegalArgumentException("不支持的ACK消息类型: " + messageType);
        }
    }
    
    @Override
    public int[] getSupportedMessageTypes() {
        return new int[]{
            MessageTypeConstants.MESSAGE_ACK,
            MessageTypeConstants.BATCH_MESSAGE_ACK,
            MessageTypeConstants.GROUP_CONVERSATION_ACK
        };
    }
    
    @Override
    public String getProcessorName() {
        return "MessageAckProcessor";
    }
}
