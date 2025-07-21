package com.vanky.im.gateway.mq;

import com.google.protobuf.InvalidProtocolBufferException;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.session.MsgSender;
import com.vanky.im.gateway.session.UserChannelManager;
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
} 