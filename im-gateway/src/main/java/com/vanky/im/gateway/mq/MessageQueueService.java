package com.vanky.im.gateway.mq;

import com.vanky.im.common.constant.TopicConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.util.MsgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import io.netty.channel.Channel;

/**
 * @author vanky
 * @create 2025/6/5
 * @description 消息队列服务，负责将消息投递到RocketMQ
 */
@Slf4j
@Service
public class MessageQueueService {

    /**
     * RocketMQ生产者
     */
    private final DefaultMQProducer producer;

    @Autowired
    public MessageQueueService(@Qualifier("defaultMQProducer") DefaultMQProducer producer) {
        this.producer = producer;
    }

    /**
     * 发送私聊消息到统一会话消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     * @param senderChannel 发送方的Channel，用于响应投递结果
     */
    public void sendMessageToPrivate(String conversationId, ChatMessage chatMessage, Channel senderChannel) {
        sendMessageToConversation(conversationId, chatMessage, senderChannel, "private");
    }

    /**
     * 发送群聊消息到统一会话消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     * @param senderChannel 发送方的Channel，用于响应投递结果
     */
    public void sendMessageToGroup(String conversationId, ChatMessage chatMessage, Channel senderChannel) {
        sendMessageToConversation(conversationId, chatMessage, senderChannel, "group");
    }

    /**
     * 发送消息到统一会话消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     * @param senderChannel 发送方的Channel，用于响应投递结果
     * @param messageTag 消息标签（private/group）
     */
    private void sendMessageToConversation(String conversationId, ChatMessage chatMessage, Channel senderChannel, String messageTag) {
        try {
            // 将ChatMessage转换为字节数组
            byte[] messageBody = chatMessage.toByteArray();

            // 创建RocketMQ消息，使用统一的会话消息主题
            Message message = new Message(TopicConstants.TOPIC_CONVERSATION_MESSAGE, messageTag, messageBody);

            // 设置消息Key为会话ID，确保同一会话的消息按顺序投递
            message.setKeys(conversationId);

            log.debug("准备发送{}消息到统一队列 - 会话ID: {}, 消息ID: {}", messageTag, conversationId, chatMessage.getUid());

            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("{}消息发送成功 - 会话ID: {}, 消息ID: {}, 发送结果: {}",
                            messageTag, conversationId, chatMessage.getUid(), sendResult);

                    // 网关层不再向客户端发送即时成功回执
                    // 成功投递到MQ的消息，将由message-server处理后，再由网关推送给客户端
                    // 因此，此处仅记录日志
                }

                @Override
                public void onException(Throwable e) {
                    log.error("{}消息发送失败 - 会话ID: {}, 消息ID: {}, 错误: {}",
                            messageTag, conversationId, chatMessage.getUid(), e.getMessage(), e);

                    // 网关层不再向客户端发送即时失败回执
                    // 投递失败的消息，可以考虑引入重试机制或记录到失败队列
                    // 此处仅记录错误日志
                }
            });

        } catch (Exception e) {
            log.error("发送{}消息到RocketMQ时发生错误 - 会话ID: {}, 消息ID: {}, 错误: {}",
                    messageTag, conversationId, chatMessage.getUid(), e.getMessage(), e);

            // 向发送方响应投递失败
            if (senderChannel != null && senderChannel.isActive()) {
                ChatMessage failedResponse = MsgGenerator.generateMessageDeliveryFailedMsg(
                        chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                senderChannel.writeAndFlush(failedResponse);
                log.debug("已向发送方响应{}投递失败 - 用户: {}, 原始消息ID: {}, 错误: {}",
                        messageTag, chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
            }
        }
    }

    /**
     * 发送ACK确认消息到消息服务器
     * @param msgId 消息ID
     * @param seq 消息序列号
     * @param userId 用户ID
     */
    public void sendAckToMessageServer(String msgId, String seq, String userId) {
        try {
            // 构建ACK消息
            ChatMessage ackMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.MESSAGE_ACK)
                    .setContent("ACK")
                    .setFromId(userId)
                    .setToId("system")
                    .setUid(msgId)
                    .setSeq(seq)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            // 序列化消息
            byte[] messageBody = ackMessage.toByteArray();

            // 创建RocketMQ消息，使用专门的ACK消息Topic
            Message message = new Message(
                    TopicConstants.TOPIC_MESSAGE_ACK,
                    "MESSAGE_ACK",
                    messageBody
            );

            // 为ACK消息设置Key，使用消息ID作为标识
            message.setKeys("ack_" + msgId);

            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("ACK消息发送成功 - 消息ID: {}, 序列号: {}, 用户: {}, Topic: {}, MQ消息ID: {}",
                            msgId, seq, userId, TopicConstants.TOPIC_MESSAGE_ACK, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("ACK消息发送失败 - 消息ID: {}, 序列号: {}, 用户: {}, Topic: {}",
                            msgId, seq, userId, TopicConstants.TOPIC_MESSAGE_ACK, e);
                }
            });

        } catch (Exception e) {
            log.error("发送ACK消息到消息队列失败 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId, e);
        }
    }

    /**
     * 发送群聊会话ACK确认消息到消息服务器
     * @param ackMessage 群聊会话ACK消息
     */
    public void sendGroupConversationAckToMessageServer(ChatMessage ackMessage) {
        try {
            String userId = ackMessage.getFromId();
            String content = ackMessage.getContent();

            log.debug("发送群聊会话ACK到消息队列 - 用户: {}, 内容: {}, Topic: {}",
                    userId, content, TopicConstants.TOPIC_MESSAGE_ACK);

            // 将群聊会话ACK消息发送到专门的ACK消息Topic
            Message message = new Message(TopicConstants.TOPIC_MESSAGE_ACK, ackMessage.toByteArray());

            // 设置消息Key为特殊格式，便于识别
            message.setKeys("group_ack_" + userId + "_" + System.currentTimeMillis());

            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("群聊会话ACK消息发送成功 - 用户: {}, Topic: {}, MsgId: {}",
                            userId, TopicConstants.TOPIC_MESSAGE_ACK, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("群聊会话ACK消息发送失败 - 用户: {}, Topic: {}",
                            userId, TopicConstants.TOPIC_MESSAGE_ACK, e);
                }
            });

        } catch (Exception e) {
            log.error("发送群聊会话ACK消息到消息队列失败 - 用户: {}, 内容: {}",
                    ackMessage.getFromId(), ackMessage.getContent(), e);
        }
    }

    /**
     * 发送消息已读回执到消息服务器
     * @param readReceiptMessage 已读回执消息
     */
    public void sendReadReceiptToMessageServer(ChatMessage readReceiptMessage) {
        try {
            String userId = readReceiptMessage.getFromId();
            String conversationId = readReceiptMessage.hasReadReceipt() ?
                    readReceiptMessage.getReadReceipt().getConversationId() : "unknown";
            long lastReadSeq = readReceiptMessage.hasReadReceipt() ?
                    readReceiptMessage.getReadReceipt().getLastReadSeq() : 0;

            log.debug("发送消息已读回执到消息队列 - 用户: {}, 会话: {}, 已读序列号: {}, Topic: {}",
                    userId, conversationId, lastReadSeq, TopicConstants.TOPIC_MESSAGE_ACK);

            // 将已读回执消息发送到专门的ACK消息Topic
            Message message = new Message(TopicConstants.TOPIC_MESSAGE_ACK, readReceiptMessage.toByteArray());

            // 设置消息Key为特殊格式，便于识别和路由
            message.setKeys("read_receipt_" + userId + "_" + conversationId + "_" + System.currentTimeMillis());

            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("已读回执消息发送成功 - 用户: {}, 会话: {}, 已读序列号: {}, MessageId: {}",
                            userId, conversationId, lastReadSeq, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("已读回执消息发送失败 - 用户: {}, 会话: {}, 已读序列号: {}",
                            userId, conversationId, lastReadSeq, e);
                }
            });

        } catch (Exception e) {
            log.error("发送已读回执到消息队列失败 - 消息: {}", readReceiptMessage, e);
        }
    }
}