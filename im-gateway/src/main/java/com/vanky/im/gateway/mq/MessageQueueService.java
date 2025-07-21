package com.vanky.im.gateway.mq;

import com.vanky.im.common.constant.TopicConstants;
import com.vanky.im.common.protocol.ChatMessage;
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
     * 会话消息主题（已废弃，使用TopicConstants中的统一主题）
     */
    @Deprecated
    private static final String CONVERSATION_TOPIC = "conversation_im_topic";
    


    /**
     * RocketMQ生产者
     */
    private final DefaultMQProducer producer;

    @Autowired
    public MessageQueueService(@Qualifier("defaultMQProducer") DefaultMQProducer producer) {
        this.producer = producer;
    }

    /**
     * 发送消息到消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     */
    public void sendMessage(String conversationId, ChatMessage chatMessage) {
        sendMessage(conversationId, chatMessage, null);
    }
    
    /**
     * 发送消息到消息队列，并向发送方响应投递结果
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     * @param senderChannel 发送方的Channel，用于响应投递结果
     */
    public void sendMessage(String conversationId, ChatMessage chatMessage, Channel senderChannel) {
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
                    
                    // 向发送方响应投递成功
                    if (senderChannel != null && senderChannel.isActive()) {
                        ChatMessage successResponse = MsgGenerator.generateMessageDeliverySuccessMsg(
                                chatMessage.getFromId(), chatMessage.getUid());
                        senderChannel.writeAndFlush(successResponse);
                        log.debug("已向发送方响应投递成功 - 用户: {}, 原始消息ID: {}", 
                                chatMessage.getFromId(), chatMessage.getUid());
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error("消息发送失败 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                            conversationId, chatMessage.getUid(), e.getMessage(), e);
                    
                    // 向发送方响应投递失败
                    if (senderChannel != null && senderChannel.isActive()) {
                        ChatMessage failedResponse = MsgGenerator.generateMessageDeliveryFailedMsg(
                                chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                        senderChannel.writeAndFlush(failedResponse);
                        log.debug("已向发送方响应投递失败 - 用户: {}, 原始消息ID: {}, 错误: {}", 
                                chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("发送消息到RocketMQ时发生错误 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                    conversationId, chatMessage.getUid(), e.getMessage(), e);
            
            // 向发送方响应投递失败
            if (senderChannel != null && senderChannel.isActive()) {
                ChatMessage failedResponse = MsgGenerator.generateMessageDeliveryFailedMsg(
                        chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                senderChannel.writeAndFlush(failedResponse);
                log.debug("已向发送方响应投递失败 - 用户: {}, 原始消息ID: {}, 错误: {}", 
                        chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
            }
        }
    }
    
    /**
     * 发送P2P消息到消息队列
     * @param conversationId 会话ID，作为消息Key
     * @param chatMessage 聊天消息
     * @param senderChannel 发送方的Channel，用于响应投递结果
     */
    public void sendMessageToP2P(String conversationId, ChatMessage chatMessage, Channel senderChannel) {
        try {
            // 将ChatMessage转换为字节数组
            byte[] messageBody = chatMessage.toByteArray();
            
            // 创建RocketMQ消息，设置主题和标签
            Message message = new Message(TopicConstants.TOPIC_MESSAGE_P2P, "p2p", messageBody);
            
            // 设置消息Key为会话ID，确保同一会话的消息按顺序投递
            message.setKeys(conversationId);
            
            log.debug("准备发送P2P消息 - 会话ID: {}, 消息ID: {}", conversationId, chatMessage.getUid());
            
            // 异步发送消息
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("P2P消息发送成功 - 会话ID: {}, 消息ID: {}, 发送结果: {}", 
                            conversationId, chatMessage.getUid(), sendResult);
                    
                    // 向发送方响应投递成功
                    if (senderChannel != null && senderChannel.isActive()) {
                        ChatMessage successResponse = MsgGenerator.generateMessageDeliverySuccessMsg(
                                chatMessage.getFromId(), chatMessage.getUid());
                        senderChannel.writeAndFlush(successResponse);
                        log.debug("已向发送方响应P2P投递成功 - 用户: {}, 原始消息ID: {}", 
                                chatMessage.getFromId(), chatMessage.getUid());
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error("P2P消息发送失败 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                            conversationId, chatMessage.getUid(), e.getMessage(), e);
                    
                    // 向发送方响应投递失败
                    if (senderChannel != null && senderChannel.isActive()) {
                        ChatMessage failedResponse = MsgGenerator.generateMessageDeliveryFailedMsg(
                                chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                        senderChannel.writeAndFlush(failedResponse);
                        log.debug("已向发送方响应P2P投递失败 - 用户: {}, 原始消息ID: {}, 错误: {}", 
                                chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("发送P2P消息到RocketMQ时发生错误 - 会话ID: {}, 消息ID: {}, 错误: {}", 
                    conversationId, chatMessage.getUid(), e.getMessage(), e);
            
            // 向发送方响应投递失败
            if (senderChannel != null && senderChannel.isActive()) {
                ChatMessage failedResponse = MsgGenerator.generateMessageDeliveryFailedMsg(
                        chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
                senderChannel.writeAndFlush(failedResponse);
                log.debug("已向发送方响应P2P投递失败 - 用户: {}, 原始消息ID: {}, 错误: {}", 
                        chatMessage.getFromId(), chatMessage.getUid(), e.getMessage());
            }
        }
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

                    // 向发送方响应投递成功
                    if (senderChannel != null && senderChannel.isActive()) {
                        ChatMessage successResponse = MsgGenerator.generateMessageDeliverySuccessMsg(
                                chatMessage.getFromId(), chatMessage.getUid());
                        senderChannel.writeAndFlush(successResponse);
                        log.debug("已向发送方响应{}投递成功 - 用户: {}, 原始消息ID: {}",
                                messageTag, chatMessage.getFromId(), chatMessage.getUid());
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error("{}消息发送失败 - 会话ID: {}, 消息ID: {}, 错误: {}",
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
}