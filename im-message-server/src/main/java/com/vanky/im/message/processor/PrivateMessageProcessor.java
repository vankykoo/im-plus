package com.vanky.im.message.processor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
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
    private MessageService messageService;

    @Autowired
    private MessageReceiverService messageReceiverService;
    
    @Autowired
    private UserMsgListService userMsgListService;
    
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private FriendshipService friendshipService;



    @Autowired
    private MessageIdempotentService messageIdempotentService;

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
        String clientSeq = chatMessage.getClientSeq();

        log.info("开始处理私聊消息 - 会话ID: {}, 发送方: {}, 接收方: {}, 原始消息ID: {}, 客户端序列号: {}",
                conversationId, fromUserId, toUserId, chatMessage.getUid(), clientSeq);

        // 0. 幂等性检查（仅对包含client_seq的消息进行检查）
        if (clientSeq != null && !clientSeq.trim().isEmpty()) {
            MessageIdempotentService.IdempotentResult idempotentResult =
                    messageIdempotentService.checkIdempotent(clientSeq);

            if (idempotentResult != null) {
                // 重复消息，直接忽略（统一推送理念：发送方通过消息拉取补偿获取消息，不主动推送）
                log.info("检测到重复私聊消息，直接忽略 - 客户端序列号: {}, 消息ID: {}, 序列号: {}",
                        clientSeq, idempotentResult.getMsgId(), idempotentResult.getSeq());

                // 不做任何推送，发送方如果需要确认，通过消息拉取补偿机制获取
                return;
            }
        }

        try {
            // 1. 关系与权限校验 (业务安全性)
            validateUserPermissions(fromUserId, toUserId);

            // 2. 消息持久化 (数据落地)
            MessagePersistResult persistResult = persistMessage(chatMessage, conversationId);

            // 3. 接收方数据持久化 (推拉结合同步模型核心：无论在线离线都生成userSeq)
            processReceiverData(chatMessage, persistResult, toUserId);

            // 4. 消息下推给接收方 (仅在线时推送)
            deliverMessageToReceiver(chatMessage, persistResult, toUserId);

            // 5. 消息下推给发送方 (统一推送逻辑 - 发送方也接收自己的消息)
            deliverMessageToSender(chatMessage, persistResult, fromUserId);

            // 6. 维护会话列表 (增强用户体验)
            updateConversationList(chatMessage, persistResult, fromUserId, toUserId);

            // 7. 数据缓存 (性能优化)
            updateCache(chatMessage, persistResult, fromUserId, toUserId);

            // 8. 记录幂等性结果（仅对包含client_seq的消息）
            if (clientSeq != null && !clientSeq.trim().isEmpty()) {
                messageIdempotentService.recordIdempotent(clientSeq, persistResult.msgId, persistResult.seq);
            }

            // 9. 最终确认通过事务管理自动完成
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

        // 2.1. 使用传入的消息ID（雪花算法生成，保持ID一致性）
        String msgId = chatMessage.getUid(); // 使用gateway传入的消息ID，避免重复生成
        Long seq = redisService.generateSeq(conversationId); // 会话内有序的序列号
        long timestamp = System.currentTimeMillis();

        log.debug("使用传入消息ID - 消息ID: {}, 会话ID: {}, Seq: {}", msgId, conversationId, seq);

        // 2.2. 消息入库
        saveMessageData(chatMessage, msgId, conversationId, seq, fromUserId, toUserId);

        return new MessagePersistResult(msgId, seq, timestamp);
    }

    /**
     * 保存消息数据到数据库
     */
    private void saveMessageData(ChatMessage chatMessage, String msgId, String conversationId,
                                Long seq, String fromUserId, String toUserId) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-07-28 23:08:31 +08:00; Reason: 使用统一的消息接收者处理逻辑;
        // }}
        // {{START MODIFICATIONS}}
        // 1. 保存消息主体到统一的message表
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_PRIVATE);
        message.setStatus(MessageConstants.MESSAGE_STATUS_SENT); // 初始状态为已发送，等待客户端确认
        messageService.save(message);
        log.debug("保存私聊消息主体完成 - 消息ID: {}, 消息类型: {}", msgId, MessageTypeConstants.MSG_TYPE_PRIVATE);

        // 2. 只为发送方生成用户级全局seq（发送方确实"接收"了自己发送的消息）
        // 接收方只有在真正接收到消息时才生成全局seq
        messageReceiverService.processSingleReceiver(fromUserId, msgId, conversationId);
        log.debug("发送方消息接收者处理完成 - 发送方: {}", fromUserId);

        // 3. 处理会话信息
        conversationService.handleConversation(conversationId, fromUserId, toUserId);
        // {{END MODIFICATIONS}}
    }

    /**
     * 3. 接收方数据持久化 (推拉结合同步模型核心)
     *
     * 核心原则：无论接收方是否在线，都必须为其生成连续的userSeq和对应的user_msg_list记录
     * 这是推拉结合同步模型的基础，确保userSeq连续性不被破坏
     */
    private void processReceiverData(ChatMessage chatMessage, MessagePersistResult persistResult, String toUserId) {
        try {
            log.debug("开始处理接收方数据持久化 - 接收方: {}, 消息ID: {}", toUserId, persistResult.msgId);

            // 为接收方生成用户级全局seq并创建user_msg_list记录
            // 这一步无论接收方在线离线都必须执行，确保userSeq连续性
            String conversationId = generateConversationId(chatMessage.getFromId(), toUserId);
            messageReceiverService.processSingleReceiver(toUserId, persistResult.msgId, conversationId);

            // 获取接收方的用户级全局序列号（用于后续推送或拉取）
            Long receiverUserSeq = redisService.getUserMaxGlobalSeq(toUserId);

            log.info("接收方数据持久化完成 - 接收方: {}, 消息ID: {}, 用户级seq: {}",
                    toUserId, persistResult.msgId, receiverUserSeq);

        } catch (Exception e) {
            log.error("接收方数据持久化失败 - 接收方: {}, 消息ID: {}", toUserId, persistResult.msgId, e);
            throw e; // 抛出异常，确保事务回滚
        }
    }

    /**
     * 4. 消息下推给接收方 (仅在线时推送)
     */
    private void deliverMessageToReceiver(ChatMessage chatMessage, MessagePersistResult persistResult, String toUserId) {
        // 查询接收方在线状态
        UserSession receiverSession = onlineStatusService.getUserOnlineStatus(toUserId);

        if (receiverSession != null && receiverSession.getNodeId() != null) {
            // 在线场景：推送消息到网关
            log.debug("接收方在线，推送消息 - 接收方: {}, 网关ID: {}", toUserId, receiverSession.getNodeId());

            // 获取接收方的用户级全局序列号（已在processReceiverData中生成）
            Long receiverUserSeq = redisService.getUserMaxGlobalSeq(toUserId);
            log.debug("获取接收方用户级全局序列号 - 接收方: {}, 用户级seq: {}", toUserId, receiverUserSeq);

            // 填充服务端生成的消息ID和序列号，包括推拉结合模式所需的序列号
            ChatMessage enrichedMessage = chatMessage.toBuilder()
                    .setUid(persistResult.msgId) // 使用服务端生成的消息ID
                    .setUserSeq(receiverUserSeq != null ? receiverUserSeq : 0L) // 设置用户级全局序列号
                    .setConversationSeq(0L) // 私聊消息不使用会话级序列号
                    .build();

            // 推送到目标gateway
            gatewayMessagePushService.pushMessageToGateway(enrichedMessage, persistResult.seq, receiverSession.getNodeId());

            log.info("私聊消息推送成功 - 接收方: {}, 消息ID: {}, 用户级seq: {}, 网关: {}",
                    toUserId, persistResult.msgId, receiverUserSeq, receiverSession.getNodeId());

        } else {
            // 离线场景：消息已安全存储为"待拉取"状态，无需额外操作
            log.info("接收方离线，消息已存储为待拉取状态 - 接收方: {}, 消息ID: {}", toUserId, persistResult.msgId);

            // 注意：不再调用offlineMessageService.addOfflineMessage()
            // 因为推拉结合模型中，客户端通过userSeq对比发现消息空洞后，会主动拉取消息
            // user_msg_list记录已在processReceiverData中创建，客户端可以通过消息拉取API获取
        }
    }

    /**
     * 6. 维护会话列表 (增强用户体验)
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
     * 7. 数据缓存 (性能优化) - 重构现有方法
     */
    private void updateCache(ChatMessage chatMessage, MessagePersistResult persistResult,
                           String fromUserId, String toUserId) {
        String conversationId = generateConversationId(fromUserId, toUserId);

        // 1. 将新消息缓存到Redis (String, msgId -> message_json, TTL 1天)
        Message message = MessageConverter.convertToMessage(chatMessage, persistResult.msgId, conversationId, MessageTypeConstants.MSG_TYPE_PRIVATE);
        String messageJson = MessageConverter.toJson(message);
        redisService.cacheMessage(persistResult.msgId, messageJson, RedisKeyConstants.MESSAGE_CACHE_TTL_SECONDS);

        // 2. 将消息的msgId和seq存入用户消息链的缓存中(ZSet)
        redisService.addToUserMsgList(fromUserId, persistResult.msgId, persistResult.seq, RedisKeyConstants.MAX_USER_MSG_CACHE_SIZE);
        redisService.addToUserMsgList(toUserId, persistResult.msgId, persistResult.seq, RedisKeyConstants.MAX_USER_MSG_CACHE_SIZE);

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
        if (content.length() <= RedisKeyConstants.MAX_MSG_CONTENT_SUMMARY_LENGTH) {
            return content;
        } else {
            return content.substring(0, RedisKeyConstants.MAX_MSG_CONTENT_SUMMARY_LENGTH) + "...";
        }
    }





    /**
     * 3.5. 消息下推给发送方 (统一推送逻辑)
     * 让发送方也接收到自己发送的消息，作为发送确认
     */
    private void deliverMessageToSender(ChatMessage chatMessage, MessagePersistResult persistResult, String fromUserId) {
        try {
            // 查询发送方在线状态
            UserSession senderSession = onlineStatusService.getUserOnlineStatus(fromUserId);

            if (senderSession != null && senderSession.getNodeId() != null) {
                log.debug("发送方在线，推送消息给发送方作为发送确认 - 发送方: {}, 网关ID: {}", fromUserId, senderSession.getNodeId());

                // 获取发送方的用户级全局序列号（已在saveMessageData中生成）
                Long senderUserSeq = redisService.getUserMaxGlobalSeq(fromUserId);
                log.debug("获取发送方用户级全局序列号 - 发送方: {}, 用户级seq: {}", fromUserId, senderUserSeq);

                // 填充服务端生成的消息ID和序列号，包括推拉结合模式所需的序列号
                ChatMessage enrichedMessage = chatMessage.toBuilder()
                        .setUid(persistResult.msgId) // 使用服务端生成的消息ID
                        .setUserSeq(senderUserSeq != null ? senderUserSeq : 0L) // 设置用户级全局序列号
                        .setConversationSeq(0L) // 私聊消息不使用会话级序列号
                        .setClientSeq(chatMessage.getClientSeq()) // 保留客户端序列号（用于重发队列匹配）
                        .setServerMsgId(persistResult.msgId) // 设置服务端消息ID（用于客户端匹配）
                        .setServerSeq(String.valueOf(persistResult.seq)) // 设置服务端序列号（用于客户端匹配）
                        .build();

                // 推送到发送方的gateway，设置targetUserId为发送方ID（因为toId是原始接收方）
                gatewayMessagePushService.pushMessageToGateway(enrichedMessage, persistResult.seq, senderSession.getNodeId(), fromUserId);

                log.info("私聊消息推送给发送方成功 - 发送方: {}, 消息ID: {}, 网关: {}",
                        fromUserId, persistResult.msgId, senderSession.getNodeId());

            } else {
                log.debug("发送方离线，跳过推送 - 发送方: {}", fromUserId);
                // 发送方离线时不需要特殊处理，因为发送方离线时本身就不会发送消息
            }

        } catch (Exception e) {
            // 发送方推送失败不影响主流程，只记录日志
            log.error("推送消息给发送方失败 - 发送方: {}, 消息ID: {}", fromUserId, persistResult.msgId, e);
        }
    }

}