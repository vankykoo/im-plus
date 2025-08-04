package com.vanky.im.message.mq;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.processor.GroupMessageProcessor;
import com.vanky.im.message.processor.MessageAckProcessor;
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

    @Autowired
    private MessageAckProcessor messageAckProcessor;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt messageExt : messages) {
                // 解析消息体
                byte[] body = messageExt.getBody();
                if (body == null || body.length == 0) {
                    log.error("消息体为空: {}", messageExt);
                    continue;
                }

                // 将字节数组转换为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);

                // 获取会话ID（从消息Key中，对于ACK消息特殊处理）
                String conversationId = getConversationId(messageExt, chatMessage);
                if (conversationId == null || conversationId.isEmpty()) {
                    // 对于ACK消息，会话ID不是必需的，可以直接处理
                    if (chatMessage.getType() == MessageTypeConstants.MESSAGE_ACK) {
                        log.debug("处理ACK消息，无需会话ID - 消息ID: {}", chatMessage.getUid());
                        processMessage(chatMessage, null);
                        continue;
                    } else {
                        log.error("消息缺少会话ID: {}", messageExt);
                        continue;
                    }
                }

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
            if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
                // 处理私聊消息
                privateMsgProcessor.processPrivateMessage(chatMessage, conversationId);
                log.info("私聊消息已处理 - 会话ID: {}, 消息ID: {}", conversationId, chatMessage.getUid());
            } else if (messageType == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
                // 处理群聊消息
                groupMsgProcessor.processGroupMessage(chatMessage, conversationId);
                log.info("群聊消息已处理 - 会话ID: {}, 消息ID: {}", conversationId, chatMessage.getUid());
            } else if (messageType == MessageTypeConstants.MESSAGE_ACK) {
                // 处理单个消息确认
                messageAckProcessor.processMessageAck(chatMessage);
                log.info("消息确认已处理 - 消息ID: {}, 用户: {}", chatMessage.getUid(), chatMessage.getFromId());
            } else if (messageType == MessageTypeConstants.BATCH_MESSAGE_ACK) {
                // 处理批量消息确认
                messageAckProcessor.processBatchMessageAck(chatMessage);
                log.info("批量消息确认已处理 - 用户: {}", chatMessage.getFromId());
            } else if (messageType == MessageTypeConstants.GROUP_CONVERSATION_ACK) {
                // 处理群聊会话ACK确认
                messageAckProcessor.processGroupConversationAck(chatMessage);
                log.info("群聊会话ACK确认已处理 - 用户: {}", chatMessage.getFromId());
            } else {
                log.warn("未知消息类型: {}, 会话ID: {}, 消息ID: {}",
                        messageType, conversationId, chatMessage.getUid());
            }
        } catch (Exception e) {
            log.error("处理消息失败 - 会话ID: {}, 消息ID: {}, 错误: {}",
                    conversationId, chatMessage.getUid(), e.getMessage(), e);
        }
    }

    /**
     * 获取会话ID，对ACK消息进行特殊处理
     * @param messageExt RocketMQ消息
     * @param chatMessage 聊天消息
     * @return 会话ID
     */
    private String getConversationId(MessageExt messageExt, ChatMessage chatMessage) {
        String keys = messageExt.getKeys();

        // 对于ACK消息，Key格式为"ack_消息ID"，需要特殊处理
        if (keys != null && keys.startsWith("ack_")) {
            // ACK消息不需要会话ID，返回null让上层逻辑处理
            return null;
        }

        // 普通聊天消息直接返回Key作为会话ID
        return keys;
    }
}
