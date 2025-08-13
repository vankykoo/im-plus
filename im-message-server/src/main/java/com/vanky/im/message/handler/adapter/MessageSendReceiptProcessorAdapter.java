package com.vanky.im.message.handler.adapter;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.MessageProcessor;
import com.vanky.im.message.processor.MessageSendReceiptProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息发送回执处理器适配器
 * 将MessageSendReceiptProcessor适配到统一的MessageProcessor接口
 * 
 * 设计原则：
 * - SRP（单一职责）：专门负责回执消息的适配和路由
 * - OCP（开放封闭）：通过适配器模式扩展功能而不修改现有代码
 * - LSP（里氏替换）：实现统一的MessageProcessor接口
 * - ISP（接口隔离）：只实现必要的接口方法
 * 
 * @author vanky
 * @since 2025-08-13
 */
@Slf4j
@Component
public class MessageSendReceiptProcessorAdapter implements MessageProcessor {
    
    @Autowired
    private MessageSendReceiptProcessor messageSendReceiptProcessor;
    
    @Override
    public void process(ChatMessage chatMessage, String conversationId) throws Exception {
        int messageType = chatMessage.getType();
        
        log.debug("处理消息发送回执 - 消息类型: {}, 发送方: {}, 客户端序列号: {}", 
                messageType, chatMessage.getFromId(), chatMessage.getClientSeq());
        
        // 验证消息类型
        if (messageType != MessageTypeConstants.MESSAGE_SEND_RECEIPT) {
            throw new IllegalArgumentException("不支持的回执消息类型: " + messageType);
        }
        
        // 调用回执处理器处理消息
        messageSendReceiptProcessor.processMessageSendReceipt(chatMessage);
    }
    
    @Override
    public int[] getSupportedMessageTypes() {
        return new int[]{MessageTypeConstants.MESSAGE_SEND_RECEIPT};
    }
    
    @Override
    public String getProcessorName() {
        return "MessageSendReceiptProcessor";
    }
}
