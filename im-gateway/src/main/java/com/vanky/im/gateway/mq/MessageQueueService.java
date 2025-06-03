package com.vanky.im.gateway.mq;

import com.vanky.im.common.protocol.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author vanky
 * @create 2025/6/5
 * @description 消息队列服务，负责将消息投递到RocketMQ
 */
@Slf4j
@Service
public class MessageQueueService {

    /**
     * 会话消息主题
     */
    private static final String CONVERSATION_TOPIC = "conversation_im_topic";

    /**
     * RocketMQ生产者
     */
    private final DefaultMQProducer producer;

    @Autowired
    public MessageQueueService(DefaultMQProducer producer) {
        this.producer = producer;
    }

    /**
     * 发送消息到消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     */
    public void sendMessage(String conversationId, ChatMessage chatMessage) {
        try {
            // 将ChatMessage转换为字节数组
            byte[] messageBody = chatMessage.toByteArray();
            
            // 创建RocketMQ消息，设置主题和标签
            Message message = new Message(CONVERSATION_TOPIC, "chat", messageBody);
            
            // 设置消息Key为会话ID，确保同一会话的消息按顺序投递
            message.setKeys(conversationId);
            
            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("消息发送成功 - 会话ID: {}, 消息ID: {}, 发送结果: {}", 
                            conversationId, chatMessage.getUid(), sendResult);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("消息发送失败 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                            conversationId, chatMessage.getUid(), e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            log.error("发送消息到RocketMQ时发生错误 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                    conversationId, chatMessage.getUid(), e.getMessage(), e);
        }
    }
}