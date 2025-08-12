package com.vanky.im.message.processor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import com.vanky.im.message.client.SequenceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私聊消息处理器
 * 
 * 核心功能：
 * 1. 在一个数据库事务内原子性生成发送方和接收方的userSeq
 * 2. 实现"写扩散"模式：为通信双方都创建消息索引记录
 * 3. 支持推拉结合的离线消息同步机制
 */
@Slf4j
@Component
public class PrivateMessageProcessor {

    @Autowired
    private RedisService redisService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageReceiverService messageReceiverService;
    
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
    private SequenceClient sequenceClient;

    @Autowired
    private OfflineMessageService offlineMessageService;

    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;

    private static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }
    }

    /**
     * 处理私聊消息完整流程
     * 核心原则：私聊使用写扩散模型，完全依赖userSeq，不使用会话级seq
     * 所有userSeq在一个事务内原子性生成和持久化
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPrivateMessage(ChatMessage chatMessage, String conversationId) {
        String fromUserId = chatMessage.getFromId();
        String toUserId = chatMessage.getToId();
        String clientSeq = chatMessage.getClientSeq();

        log.info("处理私聊消息 - 发送方: {}, 接收方: {}, 消息ID: {}, 客户端序列号: {}",
                fromUserId, toUserId, chatMessage.getUid(), clientSeq);

        // 幂等性检查
        if (clientSeq != null && !clientSeq.trim().isEmpty()) {
            MessageIdempotentService.IdempotentResult idempotentResult =
                    messageIdempotentService.checkIdempotent(clientSeq);
            if (idempotentResult != null) {
                log.info("检测到重复私聊消息，忽略 - 客户端序列号: {}, 消息ID: {}",
                        clientSeq, idempotentResult.getMsgId());
                return;
            }
        }

        try {
            // 1. 权限校验
            validateUserPermissions(fromUserId, toUserId);

            String msgId = chatMessage.getUid();

            // 2. 消息主体持久化
            persistMessage(chatMessage, msgId, conversationId);
            
            // 3. 【关键】为发送方生成userSeq并存入user_msg_list (原子性)
            Long senderUserSeq = messageReceiverService.processSingleReceiver(fromUserId, msgId, conversationId);
            
            // 4. 【关键】为接收方生成userSeq并存入user_msg_list (原子性)  
            Long receiverUserSeq = messageReceiverService.processSingleReceiver(toUserId, msgId, conversationId);

            // 5. 会话处理
            handleConversation(conversationId, fromUserId, toUserId);
            
            // 6. 消息推送 - 私聊使用各自的userSeq
            deliverMessage(chatMessage, msgId, senderUserSeq, receiverUserSeq, fromUserId, toUserId);
            
            // 7. 缓存更新 - 私聊使用各自的userSeq
            updateCache(chatMessage, msgId, senderUserSeq, receiverUserSeq, fromUserId, toUserId);

            // 8. 幂等性记录 - 私聊使用senderUserSeq作为标识
            if (clientSeq != null && !clientSeq.trim().isEmpty()) {
                messageIdempotentService.recordIdempotent(clientSeq, msgId, senderUserSeq);
            }

            log.info("私聊消息处理完成 - 消息ID: {}, 发送方seq: {}, 接收方seq: {}",
                    msgId, senderUserSeq, receiverUserSeq);

        } catch (BusinessException e) {
            log.warn("私聊消息业务校验失败 - 发送方: {}, 接收方: {}, 原因: {}",
                    fromUserId, toUserId, e.getMessage());
        } catch (Exception e) {
            log.error("处理私聊消息失败 - 发送方: {}, 接收方: {}, 消息ID: {}",
                    fromUserId, toUserId, chatMessage.getUid(), e);
            throw e;
        }
    }

    /**
     * 校验用户权限：发送者状态（封禁、禁言）+ 好友关系（拉黑）
     */
    private void validateUserPermissions(String fromUserId, String toUserId) {
        UserStatusService.UserStatusInfo senderStatus = userStatusService.getUserStatus(fromUserId);
        if (senderStatus.isBanned()) {
            throw new BusinessException(MessageConstants.ERROR_USER_BANNED + ": " + senderStatus.getReason());
        }
        if (senderStatus.isMuted()) {
            throw new BusinessException(MessageConstants.ERROR_USER_MUTED + ": " + senderStatus.getReason());
        }
        if (friendshipService.isBlocked(fromUserId, toUserId)) {
            throw new BusinessException(MessageConstants.ERROR_BLOCKED_BY_USER);
        }
    }



    /**
     * 持久化消息主体到message表
     */
    private void persistMessage(ChatMessage chatMessage, String msgId, String conversationId) {
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_PRIVATE);
        message.setStatus(MessageConstants.MESSAGE_STATUS_SENT);
        messageService.save(message);
    }

    /**
     * 处理会话相关逻辑：创建会话、激活用户会话列表、更新未读数
     */
    private void handleConversation(String conversationId, String fromUserId, String toUserId) {
        conversationService.handleConversation(conversationId, fromUserId, toUserId);
        conversationService.activateUserConversationList(fromUserId, conversationId);
        conversationService.activateUserConversationList(toUserId, conversationId);
        offlineMessageService.incrementUnreadCount(toUserId, conversationId);
    }

    /**
     * 推送消息给发送方和接收方（仅在线用户）
     * 私聊模式：每个用户使用各自的userSeq
     */
    private void deliverMessage(ChatMessage chatMessage, String msgId, 
                              Long senderUserSeq, Long receiverUserSeq, String fromUserId, String toUserId) {
        pushToReceiver(chatMessage, msgId, receiverUserSeq, toUserId);
        pushToSender(chatMessage, msgId, senderUserSeq, fromUserId);
    }

    /**
     * 推送消息给接收方（仅在线时推送）
     * 私聊模式：使用接收方的userSeq
     */
    private void pushToReceiver(ChatMessage chatMessage, String msgId, Long userSeq, String toUserId) {
        UserSession session = onlineStatusService.getUserOnlineStatus(toUserId);
        if (session != null && session.getNodeId() != null) {
            ChatMessage message = buildEnrichedMessage(chatMessage, msgId, userSeq);
            gatewayMessagePushService.pushMessageToGateway(message, userSeq, session.getNodeId());
        }
    }

    /**
     * 推送消息给发送方作为确认（包含clientSeq用于客户端匹配）
     * 私聊模式：使用发送方的userSeq
     */
    private void pushToSender(ChatMessage chatMessage, String msgId, Long userSeq, String fromUserId) {
        UserSession session = onlineStatusService.getUserOnlineStatus(fromUserId);
        if (session != null && session.getNodeId() != null) {
            ChatMessage message = buildEnrichedMessage(chatMessage, msgId, userSeq)
                    .toBuilder()
                    .setClientSeq(chatMessage.getClientSeq())  // 保留客户端序列号用于匹配
                    .setServerMsgId(msgId)
                    .build();
            gatewayMessagePushService.pushMessageToGateway(message, userSeq, session.getNodeId(), fromUserId);
        }
    }

    /**
     * 构建包含服务端信息的完整消息
     * 私聊模式：只设置userSeq，不设置conversationSeq
     */
    private ChatMessage buildEnrichedMessage(ChatMessage original, String msgId, Long userSeq) {
        return original.toBuilder()
                .setUid(msgId)                                    // 服务端消息ID
                .setUserSeq(userSeq != null ? userSeq : 0L)      // 用户级全局序列号
                // 私聊不设置conversationSeq，因为完全依赖userSeq
                .build();
    }

    /**
     * 更新Redis缓存：消息内容缓存 + 用户消息列表缓存
     * 私聊模式：为每个用户使用各自的userSeq进行缓存
     */
    private void updateCache(ChatMessage chatMessage, String msgId, Long senderUserSeq, Long receiverUserSeq,
                           String fromUserId, String toUserId) {
        String conversationId = generateConversationId(fromUserId, toUserId);
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_PRIVATE);
        String messageJson = MessageConverter.toJson(message);
        
        // 缓存消息内容
        redisService.cacheMessage(msgId, messageJson, RedisKeyConstants.MESSAGE_CACHE_TTL_SECONDS);
        
        // 为发送方和接收方分别使用各自的userSeq缓存消息列表
        redisService.addToUserMsgList(fromUserId, msgId, senderUserSeq, RedisKeyConstants.MAX_USER_MSG_CACHE_SIZE);
        redisService.addToUserMsgList(toUserId, msgId, receiverUserSeq, RedisKeyConstants.MAX_USER_MSG_CACHE_SIZE);
    }

    /**
     * 生成私聊会话ID（按字典序排序确保一致性）
     */
    private String generateConversationId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return MessageConstants.PRIVATE_CONVERSATION_PREFIX + userId1 + "_" + userId2;
        } else {
            return MessageConstants.PRIVATE_CONVERSATION_PREFIX + userId2 + "_" + userId1;
        }
    }
}