package com.vanky.im.message.processor;

import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私聊消息处理器
 * 实现写扩散模式的消息存储逻辑
 */
@Slf4j
@Component
public class PrivateMessageProcessor {

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private PrivateMessageService privateMessageService;
    
    @Autowired
    private UserMsgListService userMsgListService;
    
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private OfflineMessageService offlineMessageService;

    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;

    // 使用常量类中的配置

    /**
     * 业务异常类，用于标识不需要重试的业务错误
     */
    private static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }
    }

    /**
     * 消息持久化结果
     */
    private static class MessagePersistResult {
        public final String msgId;
        public final Long seq;
        public final long timestamp;

        public MessagePersistResult(String msgId, Long seq, long timestamp) {
            this.msgId = msgId;
            this.seq = seq;
            this.timestamp = timestamp;
        }
    }

    /**
     * 处理私聊消息的完整流程
     * 按照6个步骤：权限校验、消息持久化、消息下推、会话维护、数据缓存、最终确认
     * @param chatMessage 原始消息
     * @param conversationId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPrivateMessage(ChatMessage chatMessage, String conversationId) {
        String fromUserId = chatMessage.getFromId();
        String toUserId = chatMessage.getToId();

        log.info("开始处理私聊消息 - 会话ID: {}, 发送方: {}, 接收方: {}, 原始消息ID: {}",
                conversationId, fromUserId, toUserId, chatMessage.getUid());

        try {
            // 1. 关系与权限校验 (业务安全性)
            validateUserPermissions(fromUserId, toUserId);

            // 2. 消息持久化 (数据落地)
            MessagePersistResult persistResult = persistMessage(chatMessage, conversationId);

            // 3. 消息下推给接收方 (核心投递逻辑)
            deliverMessageToReceiver(chatMessage, persistResult, toUserId);

            // 4. 维护会话列表 (增强用户体验)
            updateConversationList(chatMessage, persistResult, fromUserId, toUserId);

            // 5. 数据缓存 (性能优化)
            updateCache(chatMessage, persistResult, fromUserId, toUserId);

            // 6. 最终确认通过事务管理自动完成
            log.info("私聊消息处理完成 - 会话ID: {}, 消息ID: {}, Seq: {}",
                    conversationId, persistResult.msgId, persistResult.seq);

        } catch (BusinessException e) {
            // 业务异常（如被拉黑）不重试，直接成功返回
            log.warn("私聊消息业务校验失败 - 发送方: {}, 接收方: {}, 原因: {}",
                    fromUserId, toUserId, e.getMessage());
            // 不抛出异常，让MQ认为消息处理成功
        } catch (Exception e) {
            log.error("处理私聊消息失败 - 发送方: {}, 接收方: {}, 原始消息ID: {}",
                    fromUserId, toUserId, chatMessage.getUid(), e);
            throw e; // 抛出异常，让MQ重试
        }
    }

    /**
     * 1. 关系与权限校验 (业务安全性)
     */
    private void validateUserPermissions(String fromUserId, String toUserId) {
        // 1.1. 发送者状态校验
        UserStatusService.UserStatusInfo senderStatus = userStatusService.getUserStatus(fromUserId);
        if (senderStatus.isBanned()) {
            log.warn("发送者被封禁，拒绝发送消息 - 发送方: {}, 原因: {}", fromUserId, senderStatus.getReason());
            // TODO: 推送系统通知给发送方说明封禁原因
            throw new BusinessException(MessageConstants.ERROR_USER_BANNED + ": " + senderStatus.getReason());
        }

        if (senderStatus.isMuted()) {
            log.warn("发送者被禁言，拒绝发送消息 - 发送方: {}, 原因: {}", fromUserId, senderStatus.getReason());
            // TODO: 推送系统通知给发送方说明禁言原因
            throw new BusinessException(MessageConstants.ERROR_USER_MUTED + ": " + senderStatus.getReason());
        }

        // 1.2. 好友关系校验
        if (friendshipService.isBlocked(fromUserId, toUserId)) {
            log.warn("发送者被接收方拉黑，伪造发送成功 - 发送方: {}, 接收方: {}", fromUserId, toUserId);
            // 为了不暴露"被拉黑"的状态，抛出特殊异常但不记录错误日志
            throw new BusinessException(MessageConstants.ERROR_BLOCKED_BY_USER);
        }

        // 检查是否为好友关系（根据产品策略决定是否允许非好友发送消息）
        if (!friendshipService.areFriends(fromUserId, toUserId)) {
            log.info("发送方和接收方不是好友关系 - 发送方: {}, 接收方: {}", fromUserId, toUserId);
            // 根据产品策略，这里可以选择拒绝或允许发送
            // 当前策略：允许发送，但可能对方不可见
        }

        log.debug("用户权限校验通过 - 发送方: {}, 接收方: {}", fromUserId, toUserId);
    }

    /**
     * 2. 消息持久化 (数据落地)
     */
    private MessagePersistResult persistMessage(ChatMessage chatMessage, String conversationId) {
        String fromUserId = chatMessage.getFromId();
        String toUserId = chatMessage.getToId();

        // 2.1. 生成消息元数据
        String msgId = MessageConverter.generateMsgId(); // 全局唯一的服务端消息ID
        Long seq = redisService.generateSeq(conversationId); // 会话内有序的序列号
        long timestamp = System.currentTimeMillis();

        log.debug("生成消息元数据 - 消息ID: {}, 会话ID: {}, Seq: {}", msgId, conversationId, seq);

        // 2.2. 消息入库
        saveMessageData(chatMessage, msgId, conversationId, seq, fromUserId, toUserId);

        return new MessagePersistResult(msgId, seq, timestamp);
    }

    /**
     * 保存消息数据到数据库
     */
    private void saveMessageData(ChatMessage chatMessage, String msgId, String conversationId, 
                                Long seq, String fromUserId, String toUserId) {
        // 1. 保存消息主体到message表
        PrivateMessage privateMessage = MessageConverter.convertToPrivateMessage(chatMessage, msgId, conversationId);
        privateMessageService.save(privateMessage);
        log.debug("保存消息主体完成 - 消息ID: {}", msgId);
        
        // 2. 写扩散：为发送方和接收方在user_msg_list表中插入记录
        userMsgListService.saveWriteExpandRecords(msgId, conversationId, seq, fromUserId, toUserId);
        log.debug("写扩散记录保存完成 - 发送方: {}, 接收方: {}", fromUserId, toUserId);
        
        // 3. 处理会话信息
        conversationService.handleConversation(conversationId, fromUserId, toUserId);
    }

    /**
     * 3. 消息下推给接收方 (核心投递逻辑)
     */
    private void deliverMessageToReceiver(ChatMessage chatMessage, MessagePersistResult persistResult, String toUserId) {
        // 3.1. 查询接收方在线状态
        UserSession receiverSession = onlineStatusService.getUserOnlineStatus(toUserId);

        if (receiverSession != null && receiverSession.getNodeId() != null) {
            // 3.2. 处理在线场景
            log.debug("接收方在线，推送消息 - 接收方: {}, 网关ID: {}", toUserId, receiverSession.getNodeId());

            // 填充服务端生成的消息ID和序列号
            ChatMessage enrichedMessage = chatMessage.toBuilder()
                    .setUid(persistResult.msgId) // 使用服务端生成的消息ID
                    .build();

            // 推送到目标gateway
            gatewayMessagePushService.pushMessageToGateway(enrichedMessage, persistResult.seq, receiverSession.getNodeId());

        } else {
            // 3.3. 处理离线场景
            log.debug("接收方离线，添加到离线消息队列 - 接收方: {}, 消息ID: {}", toUserId, persistResult.msgId);

            // 将消息ID存入离线消息队列
            offlineMessageService.addOfflineMessage(toUserId, persistResult.msgId);
        }
    }

    /**
     * 4. 维护会话列表 (增强用户体验)
     */
    private void updateConversationList(ChatMessage chatMessage, MessagePersistResult persistResult,
                                      String fromUserId, String toUserId) {
        String conversationId = generateConversationId(fromUserId, toUserId);
        String msgContent = getMessageContentSummary(chatMessage);

        // 4.1. 更新会话信息
        conversationService.updateConversationLatestMessage(
                conversationId, persistResult.msgId, msgContent, persistResult.timestamp, fromUserId);

        // 4.2. 激活会话（发送方和接收方）
        conversationService.activateUserConversationList(fromUserId, conversationId);
        conversationService.activateUserConversationList(toUserId, conversationId);

        // 4.3. 为接收方增加未读数
        long newUnreadCount = offlineMessageService.incrementUnreadCount(toUserId, conversationId);
        log.debug("更新接收方未读数 - 接收方: {}, 会话ID: {}, 新未读数: {}", toUserId, conversationId, newUnreadCount);
    }

    /**
     * 5. 数据缓存 (性能优化) - 重构现有方法
     */
    private void updateCache(ChatMessage chatMessage, MessagePersistResult persistResult,
                           String fromUserId, String toUserId) {
        String conversationId = generateConversationId(fromUserId, toUserId);

        // 1. 将新消息缓存到Redis (String, msgId -> message_json, TTL 1天)
        PrivateMessage privateMessage = MessageConverter.convertToPrivateMessage(chatMessage, persistResult.msgId, conversationId);
        String messageJson = MessageConverter.toJson(privateMessage);
        redisService.cacheMessage(persistResult.msgId, messageJson, MessageConstants.MESSAGE_CACHE_TTL_SECONDS);

        // 2. 将消息的msgId和seq存入用户消息链的缓存中(ZSet)
        redisService.addToUserMsgList(fromUserId, persistResult.msgId, persistResult.seq, MessageConstants.MAX_USER_MSG_CACHE_SIZE);
        redisService.addToUserMsgList(toUserId, persistResult.msgId, persistResult.seq, MessageConstants.MAX_USER_MSG_CACHE_SIZE);

        log.debug("缓存更新完成 - 消息ID: {}, 发送方: {}, 接收方: {}", persistResult.msgId, fromUserId, toUserId);
    }

    // ========== 辅助方法 ==========

    /**
     * 生成会话ID
     */
    private String generateConversationId(String userId1, String userId2) {
        // 按字典序排序，确保会话ID的一致性
        if (userId1.compareTo(userId2) <= 0) {
            return MessageConstants.PRIVATE_CONVERSATION_PREFIX + userId1 + "_" + userId2;
        } else {
            return MessageConstants.PRIVATE_CONVERSATION_PREFIX + userId2 + "_" + userId1;
        }
    }

    /**
     * 获取消息内容摘要
     */
    private String getMessageContentSummary(ChatMessage chatMessage) {
        String content = chatMessage.getContent();
        if (content == null || content.isEmpty()) {
            return MessageConstants.EMPTY_MESSAGE_PLACEHOLDER;
        }

        // 限制摘要长度
        if (content.length() <= MessageConstants.MAX_MSG_CONTENT_SUMMARY_LENGTH) {
            return content;
        } else {
            return content.substring(0, MessageConstants.MAX_MSG_CONTENT_SUMMARY_LENGTH) + "...";
        }
    }
}