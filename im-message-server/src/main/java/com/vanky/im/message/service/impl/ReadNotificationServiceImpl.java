package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.ReadNotification;
import com.vanky.im.message.service.RedisService;
import com.vanky.im.message.service.ReadNotificationService;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.service.GatewayMessagePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 已读通知服务实现
 * 
 * @author vanky
 * @since 2025-08-12
 */
@Slf4j
@Service
public class ReadNotificationServiceImpl implements ReadNotificationService {

    @Autowired
    private MessageService messageService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;

    @Override
    public void sendPrivateReadNotification(String conversationId, String readerId, long lastReadSeq) {
        log.debug("发送私聊已读通知 - 会话: {}, 已读用户: {}, 已读seq: {}", conversationId, readerId, lastReadSeq);

        try {
            // 解析会话ID获取发送方和接收方
            String[] parts = conversationId.split("_");
            if (parts.length != 3 || !"private".equals(parts[0])) {
                log.warn("无效的私聊会话ID格式 - 会话: {}", conversationId);
                return;
            }

            String user1 = parts[1];
            String user2 = parts[2];
            
            // 确定消息发送方（不是已读用户的另一方）
            String senderId = readerId.equals(user1) ? user2 : user1;

            // 检查发送方是否在线
            var senderSession = redisService.getUserSession(senderId);
            if (senderSession == null || senderSession.getNodeId() == null) {
                log.debug("消息发送方不在线，跳过已读通知 - 发送方: {}", senderId);
                return;
            }

            // 构建已读通知消息
            ReadNotification readNotification = ReadNotification.newBuilder()
                    .setConversationId(conversationId)
                    .setLastReadSeq(lastReadSeq)
                    .build();

            ChatMessage notificationMessage = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.MESSAGE_READ_NOTIFICATION)
                    .setFromId(readerId)
                    .setToId(senderId)
                    .setConversationId(conversationId)
                    .setTimestamp(System.currentTimeMillis())
                    .setReadNotification(readNotification)
                    .build();

            // 推送通知到网关
            gatewayMessagePushService.pushMessageToGateway(notificationMessage, 0L, senderSession.getNodeId(), senderId);
            
            log.info("私聊已读通知发送成功 - 会话: {}, 发送方: {}, 已读seq: {}", conversationId, senderId, lastReadSeq);

        } catch (Exception e) {
            log.error("发送私聊已读通知失败 - 会话: {}, 已读用户: {}, 错误: {}", conversationId, readerId, e.getMessage(), e);
        }
    }

    @Override
    public void sendGroupReadNotifications(String conversationId, List<String> messageIds, String readerId) {
        log.debug("发送群聊已读通知 - 会话: {}, 消息数量: {}, 已读用户: {}", conversationId, messageIds.size(), readerId);

        try {
            // 获取消息的发送方信息
            Map<String, String> msgSenderMap = messageService.getMessageSenders(messageIds);

            // 按发送方分组消息
            Map<String, List<String>> senderMsgMap = messageIds.stream()
                    .filter(msgId -> msgSenderMap.containsKey(msgId))
                    .collect(Collectors.groupingBy(msgId -> msgSenderMap.get(msgId)));

            // 为每个发送方批量发送已读通知
            for (Map.Entry<String, List<String>> entry : senderMsgMap.entrySet()) {
                String senderId = entry.getKey();
                List<String> senderMsgIds = entry.getValue();

                // 跳过已读用户自己发送的消息
                if (readerId.equals(senderId)) {
                    log.debug("跳过用户自己发送的消息 - 用户: {}, 消息数量: {}", readerId, senderMsgIds.size());
                    continue;
                }

                // 批量发送已读通知
                sendGroupReadNotificationToSenderBatch(conversationId, senderId, senderMsgIds, readerId);
            }

        } catch (Exception e) {
            log.error("发送群聊已读通知失败 - 会话: {}, 已读用户: {}, 错误: {}", conversationId, readerId, e.getMessage(), e);
        }
    }

    /**
     * 向指定发送方批量发送群聊已读通知（优化版本）
     */
    private void sendGroupReadNotificationToSenderBatch(String conversationId, String senderId, List<String> messageIds, String readerId) {
        // 检查发送方是否在线
        var senderSession = redisService.getUserSession(senderId);
        if (senderSession == null || senderSession.getNodeId() == null) {
            log.debug("消息发送方不在线，跳过已读通知 - 发送方: {}", senderId);
            return;
        }

        // 批量处理消息，减少网络开销
        int batchSize = 10; // 每批处理10条消息
        for (int i = 0; i < messageIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, messageIds.size());
            List<String> batchMsgIds = messageIds.subList(i, endIndex);

            try {
                // 为这批消息发送已读通知
                sendBatchReadNotifications(conversationId, senderId, batchMsgIds, readerId, senderSession.getNodeId());

                log.debug("批量群聊已读通知发送成功 - 发送方: {}, 消息数量: {}", senderId, batchMsgIds.size());

            } catch (Exception e) {
                log.error("发送批量群聊已读通知失败 - 发送方: {}, 消息数量: {}, 错误: {}",
                        senderId, batchMsgIds.size(), e.getMessage(), e);

                // 批量失败时，回退到单条发送
                fallbackToSingleNotifications(conversationId, senderId, batchMsgIds, readerId, senderSession.getNodeId());
            }
        }
    }

    /**
     * 发送批量已读通知
     */
    private void sendBatchReadNotifications(String conversationId, String senderId, List<String> messageIds,
                                          String readerId, String nodeId) {
        for (String msgId : messageIds) {
            try {
                // 获取当前消息的已读数
                int readCount = getGroupMessageReadCount(msgId);

                // 构建已读通知消息
                ReadNotification readNotification = ReadNotification.newBuilder()
                        .setConversationId(conversationId)
                        .setMsgId(msgId)
                        .setReadCount(readCount)
                        .build();

                ChatMessage notificationMessage = ChatMessage.newBuilder()
                        .setType(MessageTypeConstants.MESSAGE_READ_NOTIFICATION)
                        .setFromId(readerId)
                        .setToId(senderId)
                        .setConversationId(conversationId)
                        .setTimestamp(System.currentTimeMillis())
                        .setReadNotification(readNotification)
                        .build();

                // 推送通知到网关
                gatewayMessagePushService.pushMessageToGateway(notificationMessage, 0L, nodeId, senderId);

                log.debug("群聊已读通知发送成功 - 消息: {}, 发送方: {}, 已读数: {}", msgId, senderId, readCount);

            } catch (Exception e) {
                log.error("发送单条群聊已读通知失败 - 消息: {}, 发送方: {}, 错误: {}", msgId, senderId, e.getMessage(), e);
                // 单条失败不影响其他消息的通知发送
            }
        }
    }

    /**
     * 批量发送失败时的回退处理
     */
    private void fallbackToSingleNotifications(String conversationId, String senderId, List<String> messageIds,
                                             String readerId, String nodeId) {
        log.warn("批量发送失败，回退到单条发送 - 发送方: {}, 消息数量: {}", senderId, messageIds.size());

        for (String msgId : messageIds) {
            try {
                // 获取当前消息的已读数
                int readCount = getGroupMessageReadCount(msgId);

                // 构建已读通知消息
                ReadNotification readNotification = ReadNotification.newBuilder()
                        .setConversationId(conversationId)
                        .setMsgId(msgId)
                        .setReadCount(readCount)
                        .build();

                ChatMessage notificationMessage = ChatMessage.newBuilder()
                        .setType(MessageTypeConstants.MESSAGE_READ_NOTIFICATION)
                        .setFromId(readerId)
                        .setToId(senderId)
                        .setConversationId(conversationId)
                        .setTimestamp(System.currentTimeMillis())
                        .setReadNotification(readNotification)
                        .build();

                // 推送通知到网关
                gatewayMessagePushService.pushMessageToGateway(notificationMessage, 0L, nodeId, senderId);

                log.debug("回退单条群聊已读通知发送成功 - 消息: {}, 发送方: {}, 已读数: {}", msgId, senderId, readCount);

            } catch (Exception e) {
                log.error("回退单条群聊已读通知发送失败 - 消息: {}, 发送方: {}, 错误: {}", msgId, senderId, e.getMessage(), e);
            }
        }
    }

    @Override
    public int getGroupMessageReadCount(String msgId) {
        try {
            return redisService.getGroupReadCount(msgId);
        } catch (Exception e) {
            log.error("获取群聊消息已读数失败 - 消息: {}, 错误: {}", msgId, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<String> getGroupMessageReadUsers(String msgId) {
        try {
            return redisService.getGroupReadUsers(msgId);
        } catch (Exception e) {
            log.error("获取群聊消息已读用户列表失败 - 消息: {}, 错误: {}", msgId, e.getMessage(), e);
            return List.of();
        }
    }
}
