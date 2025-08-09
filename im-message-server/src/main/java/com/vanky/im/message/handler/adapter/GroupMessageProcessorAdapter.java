package com.vanky.im.message.handler.adapter;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.MessageProcessor;
import com.vanky.im.message.processor.GroupMessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 群聊消息处理器适配器
 * 将现有的GroupMessageProcessor适配到统一的MessageProcessor接口
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 群聊消息处理器适配器，实现统一消息处理接口
 */
@Slf4j
@Component
public class GroupMessageProcessorAdapter implements MessageProcessor {
    
    @Autowired
    private GroupMessageProcessor groupMessageProcessor;
    
    @Override
    public void process(ChatMessage chatMessage, String conversationId) throws Exception {
        // 调用原有的群聊消息处理逻辑
        groupMessageProcessor.processGroupMessage(chatMessage, conversationId);
    }
    
    @Override
    public int[] getSupportedMessageTypes() {
        return new int[]{MessageTypeConstants.GROUP_CHAT_MESSAGE};
    }
    
    @Override
    public String getProcessorName() {
        return "GroupMessageProcessor";
    }
}
