package com.vanky.im.gateway.mq;

import com.google.protobuf.InvalidProtocolBufferException;
import com.vanky.im.common.constant.MessageTypeConstants;
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
                // {{CHENGQI:
                // Action: Modified; Timestamp: 2025-08-03 13:55:00 +08:00; Reason: 增强Protobuf解析错误处理，避免损坏消息导致无限重试;
                // }}
                // {{START MODIFICATIONS}}
                // 消息完整性检查
                if (body == null || body.length == 0) {
                    log.warn("收到空消息体 - MsgId: {}, Topic: {}, Tags: {}", msg.getMsgId(), topic, tags);
                    continue; // 跳过空消息
                }

                if (body.length > 4 * 1024 * 1024) { // 4MB限制
                    log.warn("消息体过大 - MsgId: {}, 大小: {} bytes", msg.getMsgId(), body.length);
                    continue; // 跳过过大消息
                }

                log.debug("准备解析消息 - MsgId: {}, 消息体大小: {} bytes, 前8字节: {}",
                        msg.getMsgId(), body.length, bytesToHex(body, 8));

                // 解析消息体为ChatMessage对象
                ChatMessage chatMessage = ChatMessage.parseFrom(body);
                // {{END MODIFICATIONS}}

                // 获取接收方用户ID，优先从消息属性中获取targetUserId（群聊场景）
                String targetUserId = msg.getUserProperty("targetUserId");
                String toUserId = targetUserId != null ? targetUserId : chatMessage.getToId();

                log.debug("消息推送处理 - 原始接收方: {}, 目标用户: {}, 会话ID: {}",
                        chatMessage.getToId(), toUserId, chatMessage.getConversationId());

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
                // {{CHENGQI:
                // Action: Modified; Timestamp: 2025-08-03 13:55:00 +08:00; Reason: 优化Protobuf解析错误处理，记录详细调试信息并跳过损坏消息;
                // }}
                // {{START MODIFICATIONS}}
                log.error("Protobuf解析失败，消息可能损坏 - MsgId: {}, Topic: {}, Tags: {}, Keys: {}, " +
                        "QueueId: {}, 消息体大小: {} bytes, 前16字节: {}, 错误: {}",
                        msg.getMsgId(), topic, tags, keys, msg.getQueueId(),
                        body != null ? body.length : 0,
                        body != null ? bytesToHex(body, 16) : "null",
                        e.getMessage());

                // 对于明显损坏的消息，不再重试，直接跳过，避免无限重试阻塞消费者
                log.warn("跳过损坏的消息，避免无限重试 - MsgId: {}", msg.getMsgId());
                continue; // 跳过当前消息，继续处理下一条
                // {{END MODIFICATIONS}}
            } catch (Exception e) {
                log.error("处理推送消息异常 - MsgId: {}, Topic: {}, Tags: {}, Keys: {}",
                        msg.getMsgId(), topic, tags, keys, e);
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
            if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE ||
                messageType == MessageTypeConstants.GROUP_CHAT_MESSAGE) {

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

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     * @param bytes 字节数组
     * @param maxLength 最大长度
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes, int maxLength) {
        if (bytes == null) {
            return "null";
        }

        int length = Math.min(bytes.length, maxLength);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if (i < length - 1) {
                sb.append(" ");
            }
        }

        if (bytes.length > maxLength) {
            sb.append("...");
        }

        return sb.toString();
    }
}