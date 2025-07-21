package com.vanky.im.message.mq;

import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.processor.GroupMessageProcessor;
import com.vanky.im.message.processor.PrivateMessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一会话消息消费者
 * 处理私聊和群聊消息的统一消费逻辑
 */
@Slf4j
@Component
public class ConversationMessageConsumer implements MessageListenerConcurrently {

    @Autowired
    private PrivateMessageProcessor privateMsgProcessor;
    
    @Autowired
    private GroupMessageProcessor groupMsgProcessor;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt messageExt : messages) {
                // 获取会话ID（从消息Key中）
                String conversationId = messageExt.getKeys();
                if (conversationId == null || conversationId.isEmpty()) {
                    log.error("消息缺少会话ID: {}", messageExt);
                    continue;
                }
                
                // 解析消息体
                byte[] body = messageExt.getBody();
                if (body == null || body.length == 0) {
                    log.error("消息体为空: {}", messageExt);
                    continue;
                }
                
                // 将字节数组转换为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);
                
                // 处理消息
                processMessage(chatMessage, conversationId);
            }
            
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            log.error("消费消息时发生错误", e);
            // 稍后重试
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
    
    /**
     * 处理消息，根据消息类型分发
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID
     */
    private void processMessage(ChatMessage chatMessage, String conversationId) {
        try {
            int messageType = chatMessage.getType();
            
            // 根据消息类型分发处理
            if (messageType == ClientToClientMessageType.PRIVATE_CHAT_MESSAGE.getValue()) {
                // 处理私聊消息
                privateMsgProcessor.processPrivateMessage(chatMessage, conversationId);
                log.info("私聊消息已处理 - 会话ID: {}, 消息ID: {}", conversationId, chatMessage.getUid());
            } else if (messageType == ClientToClientMessageType.GROUP_CHAT_MESSAGE.getValue()) {
                // 处理群聊消息
                groupMsgProcessor.processGroupMessage(chatMessage, conversationId);
                log.info("群聊消息已处理 - 会话ID: {}, 消息ID: {}", conversationId, chatMessage.getUid());
            } else {
                log.warn("未知消息类型: {}, 会话ID: {}, 消息ID: {}", 
                        messageType, conversationId, chatMessage.getUid());
            }
        } catch (Exception e) {
            log.error("处理消息失败 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                    conversationId, chatMessage.getUid(), e.getMessage(), e);
        }
    }
}
