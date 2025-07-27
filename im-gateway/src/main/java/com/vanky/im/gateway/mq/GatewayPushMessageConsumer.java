package com.vanky.im.gateway.mq;

import com.google.protobuf.InvalidProtocolBufferException;
import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.session.MsgSender;
import com.vanky.im.gateway.session.UserChannelManager;
import com.vanky.im.gateway.timeout.TimeoutManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gateway推送消息消费者，负责接收并处理发送到当前网关的消息
 */
@Slf4j
@Component
public class GatewayPushMessageConsumer implements MessageListenerConcurrently {
    
    @Autowired
    private MsgSender msgSender;

    @Autowired
    private UserChannelManager userChannelManager;

    @Autowired
    private TimeoutManager timeoutManager;
    
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String topic = msg.getTopic();
            String tags = msg.getTags();
            String keys = msg.getKeys();
            byte[] body = msg.getBody();
            
            log.debug("收到推送消息 - Topic: {}, Tags: {}, Keys: {}, MsgId: {}, QueueId: {}",
                    topic, tags, keys, msg.getMsgId(), msg.getQueueId());
            
            try {
                // 解析消息体为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);
                
                // 获取接收方用户ID
                String toUserId = chatMessage.getToId();
                
                // 检查用户是否在当前网关在线
                if (userChannelManager.isUserOnline(toUserId)) {
                    // 发送消息给用户
                    boolean success = msgSender.sendToUser(toUserId, chatMessage);

                    if (success) {
                        log.info("消息推送成功 - 接收方: {}, 消息ID: {}", toUserId, chatMessage.getUid());

                        // 只有真正推送给客户端的聊天消息才需要超时重发机制
                        addTimeoutTaskForChatMessage(chatMessage, toUserId);
                    } else {
                        log.warn("消息推送失败 - 接收方: {}, 消息ID: {}", toUserId, chatMessage.getUid());
                    }
                } else {
                    // 用户不在线，记录日志
                    log.info("接收方不在当前网关在线，跳过推送 - 接收方: {}, 消息ID: {}",
                            toUserId, chatMessage.getUid());
                }
                
            } catch (InvalidProtocolBufferException e) {
                log.error("解析消息失败 - MsgId: {}", msg.getMsgId(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } catch (Exception e) {
                log.error("处理推送消息异常 - MsgId: {}", msg.getMsgId(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 为聊天消息添加超时重发任务
     * 只有真正推送给客户端的私聊、群聊消息才需要超时重发机制
     *
     * @param chatMessage 聊天消息
     * @param toUserId 接收方用户ID
     */
    private void addTimeoutTaskForChatMessage(ChatMessage chatMessage, String toUserId) {
        try {
            // 只为聊天消息添加超时任务，不为系统消息添加
            int messageType = chatMessage.getType();
            if (messageType == ClientToClientMessageType.PRIVATE_CHAT_MESSAGE.getValue() ||
                messageType == ClientToClientMessageType.GROUP_CHAT_MESSAGE.getValue()) {

                String ackId = chatMessage.getUid();

                // 添加超时任务
                timeoutManager.addTask(ackId, chatMessage, toUserId);

                log.debug("为下行消息添加超时任务 - 消息ID: {}, 接收方: {}, 消息类型: {}",
                        ackId, toUserId, messageType);
            }
        } catch (Exception e) {
            log.error("添加超时任务失败 - 消息ID: {}, 接收方: {}, 消息类型: {}",
                    chatMessage.getUid(), toUserId, chatMessage.getType(), e);
        }
    }
}