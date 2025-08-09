package com.vanky.im.message.mq;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.handler.ImMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACK消息专用消费者
 * 使用并发消费模式，专门处理各种ACK确认消息
 * 
 * @author vanky
 * @create 2025-08-09
 * @description 专门处理ACK消息的消费者，与业务消息分离，避免竞争条件
 */
@Slf4j
@Component
public class MessageAckConsumer implements MessageListenerConcurrently {
    
    @Autowired
    private ImMessageHandler messageHandler;
    
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt messageExt : messages) {
                // 解析消息体
                byte[] body = messageExt.getBody();
                if (body == null || body.length == 0) {
                    log.error("ACK消息体为空: {}", messageExt);
                    continue;
                }
                
                // 将字节数组转换为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);
                
                // 验证是否为ACK类型消息
                if (!isAckMessage(chatMessage.getType())) {
                    log.error("非ACK消息类型被路由到ACK消费者 - 消息类型: {}, 消息ID: {}", 
                            chatMessage.getType(), chatMessage.getUid());
                    continue;
                }
                
                // 处理ACK消息（ACK消息不需要会话ID）
                messageHandler.handleMessage(chatMessage, null);
                
                log.debug("ACK消息处理完成 - 类型: {}, 消息ID: {}, 用户: {}", 
                        chatMessage.getType(), chatMessage.getUid(), chatMessage.getFromId());
            }
            
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            log.error("ACK消息消费时发生错误", e);
            // 稍后重试
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
    
    /**
     * 判断是否为ACK类型消息
     * 
     * @param messageType 消息类型
     * @return true-ACK消息，false-其他消息
     */
    private boolean isAckMessage(int messageType) {
        return messageType == MessageTypeConstants.MESSAGE_ACK ||
               messageType == MessageTypeConstants.BATCH_MESSAGE_ACK ||
               messageType == MessageTypeConstants.GROUP_CONVERSATION_ACK;
    }
}
