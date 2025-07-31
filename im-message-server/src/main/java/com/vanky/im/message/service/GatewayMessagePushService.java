package com.vanky.im.message.service;

import com.vanky.im.common.protocol.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 网关消息推送服务
 * 负责将消息推送到指定的网关进行用户投递
 */
@Service
public class GatewayMessagePushService {

    private static final Logger log = LoggerFactory.getLogger(GatewayMessagePushService.class);
    
    @Value("${message.push.topic}")
    private String pushToGatewayTopic;
    
    @Autowired
    @Qualifier("gatewayPushProducer")
    private DefaultMQProducer producer;
    
    /**
     * 推送消息到指定网关
     * 
     * @param chatMessage 聊天消息
     * @param seq 序列号
     * @param gatewayId 网关ID
     */
    public void pushMessageToGateway(ChatMessage chatMessage, Long seq, String gatewayId) {
        try {
            // 创建消息并设置Tag为网关ID
            Message message = new Message();
            message.setTopic(pushToGatewayTopic);
            message.setTags(gatewayId);
            message.setBody(chatMessage.toByteArray());
            
            // 设置序列号作为消息键，方便追踪
            message.setKeys(String.valueOf(seq));
            
            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("消息推送成功 - 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}, 消息结果: {}", 
                            chatMessage.getToId(), chatMessage.getUid(), seq, gatewayId, sendResult);
                }
                
                @Override
                public void onException(Throwable e) {
                    log.error("消息推送失败 - 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}", 
                            chatMessage.getToId(), chatMessage.getUid(), seq, gatewayId, e);
                }
            });
            
        } catch (Exception e) {
            log.error("推送消息到网关异常 - 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}", 
                    chatMessage.getToId(), chatMessage.getUid(), seq, gatewayId, e);
        }
    }
} 