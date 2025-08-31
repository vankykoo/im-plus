package com.vanky.im.message.mq;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.ImMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一会话消息消费者
 * 处理私聊和群聊消息的统一消费逻辑
 * 使用顺序消费模式确保同一会话内消息的严格顺序性
 * 使用统一消息分发器处理消息路由和异常处理
 */
@Slf4j
@Component
public class ConversationMessageConsumer implements MessageListenerOrderly {

    @Autowired
    private ImMessageHandler messageHandler;

    @Override
    public ConsumeOrderlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeOrderlyContext context) {
        try {
            for (MessageExt messageExt : messages) {
                // 设置处理开始时间
                MessageProcessingTimeHolder.setStartTime(System.currentTimeMillis());
                // 解析消息体
                byte[] body = messageExt.getBody();
                if (body == null || body.length == 0) {
                    log.error("消息体为空: {}", messageExt);
                    continue;
                }

                // 将字节数组转换为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);

                // 获取会话ID（从消息Key中）
                String conversationId = getConversationId(messageExt);
                if (conversationId == null || conversationId.isEmpty()) {
                    log.error("会话消息缺少会话ID: {}", messageExt);
                    continue;
                }

                // 使用统一消息分发器处理消息
                try {
                    messageHandler.handleMessage(chatMessage, conversationId);
                } finally {
                    // 清理时间戳，避免内存泄漏
                    MessageProcessingTimeHolder.clear();
                }
            }
            
            return ConsumeOrderlyStatus.SUCCESS;
        } catch (Exception e) {
            log.error("消费消息时发生错误", e);
            // 暂停当前队列一段时间后重试
            return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
        }
    }
    


    /**
     * 获取会话ID
     * @param messageExt RocketMQ消息
     * @return 会话ID
     */
    private String getConversationId(MessageExt messageExt) {
        // 会话消息的Key就是会话ID
        return messageExt.getKeys();
    }
}
