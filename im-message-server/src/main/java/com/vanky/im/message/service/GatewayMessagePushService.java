package com.vanky.im.message.service;

import com.vanky.im.common.constant.TopicConstants;
import com.vanky.im.common.model.UserSession;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
/**
 * 网关消息推送服务
 * 负责将消息推送到指定的网关进行用户投递
 */
@Service
public class GatewayMessagePushService {

    private static final Logger log = LoggerFactory.getLogger(GatewayMessagePushService.class);
    
    @Autowired
    @Qualifier("gatewayPushProducer")
    private DefaultMQProducer producer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${rocketmq.topic.push-to-gateway:TOPIC_PUSH_TO_GATEWAY}")
    private String pushToGatewayTopic;

    /**
     * 推送消息到网关（支持指定目标用户ID）
     *
     * @param chatMessage 聊天消息
     * @param seq 序列号
     * @param targetUserId 目标用户ID（群聊时使用，私聊时为null）
     */
    public void pushMessageToGateway(ChatMessage chatMessage, Long seq, String targetUserId) {
        String toId = targetUserId != null ? targetUserId : chatMessage.getToId();
        UserSession userSession = (UserSession) redisTemplate.opsForValue().get(com.vanky.im.common.constant.SessionConstants.getUserSessionKey(toId));

        if (userSession == null) {
            log.warn("用户 {} 不在线，消息将转为离线消息处理", toId);
            // TODO: 添加离线消息处理逻辑
            return;
        }

        try {
            Message message = new Message();
            message.setTopic(TopicConstants.TOPIC_PUSH_TO_GATEWAY);
            message.setBody(chatMessage.toByteArray());

            // 设置序列号作为消息键，方便追踪
            message.setKeys(String.valueOf(seq));

            // 如果指定了目标用户ID，将其添加到消息属性中
            if (targetUserId != null) {
                message.putUserProperty("targetUserId", targetUserId);
            }

            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("消息推送到共享Topic成功 - 接收方: {}, 目标用户: {}, 消息ID: {}, 序列号: {}, 消息结果: {}",
                            chatMessage.getToId(), targetUserId, chatMessage.getUid(), seq, sendResult);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("消息推送到共享Topic失败 - 接收方: {}, 目标用户: {}, 消息ID: {}, 序列号: {}",
                            chatMessage.getToId(), targetUserId, chatMessage.getUid(), seq, e);
                }
            });

        } catch (Exception e) {
            log.error("推送消息到共享Topic异常 - 接收方: {}, 目标用户: {}, 消息ID: {}, 序列号: {}",
                    chatMessage.getToId(), targetUserId, chatMessage.getUid(), seq, e);
        }
    }

    /**
     * 推送群聊通知到指定网关（读扩散模式）
     * 使用ChatMessage协议承载通知信息，不需要额外的消息属性
     *
     * @param notificationMessage 通知消息（ChatMessage格式，toId为目标用户ID）
     * @param seq 序列号
     * @param gatewayId 网关ID
     */
    public void pushNotificationToGateway(ChatMessage notificationMessage, Long seq, String gatewayId) {
        try {
            // {{CHENGQI:
            // Action: Modified; Timestamp: 2025-08-02 22:32:38 +08:00; Reason: 简化群聊通知推送，移除不必要的消息属性，直接使用ChatMessage协议中的字段;
            // }}
            // {{START MODIFICATIONS}}
            // 创建消息并设置Tag为网关ID
            Message message = new Message();
            message.setTopic(pushToGatewayTopic);
            message.setTags(gatewayId);
            message.setBody(notificationMessage.toByteArray());

            // 设置序列号作为消息键，方便追踪
            message.setKeys(String.valueOf(seq));

            // 不需要额外的消息属性，ChatMessage协议中已包含所有必要信息：
            // - conversationId: 标识群聊会话
            // - fromId: 发送方用户ID
            // - toId: 接收方用户ID（目标用户）

            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("群聊通知推送成功 - 会话ID: {}, 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}, 消息结果: {}",
                            notificationMessage.getConversationId(), notificationMessage.getToId(),
                            notificationMessage.getUid(), seq, gatewayId, sendResult);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("群聊通知推送失败 - 会话ID: {}, 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}",
                            notificationMessage.getConversationId(), notificationMessage.getToId(),
                            notificationMessage.getUid(), seq, gatewayId, e);
                }
            });
            // {{END MODIFICATIONS}}

        } catch (Exception e) {
            log.error("推送群聊通知到网关异常 - 会话ID: {}, 接收方: {}, 消息ID: {}, 序列号: {}, 网关: {}",
                    notificationMessage.getConversationId(), notificationMessage.getToId(),
                    notificationMessage.getUid(), seq, gatewayId, e);
        }
    }
}