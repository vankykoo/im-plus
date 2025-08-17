package com.vanky.im.message.processor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import com.vanky.im.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私聊消息处理器
 * 负责私聊消息的完整处理流程：权限校验、消息持久化、序列号生成、消息推送和缓存更新
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
    private GatewayMessagePushService gatewayMessagePushService;

    @Autowired
    private MessageSendReceiptService messageSendReceiptService;

    // 雪花算法ID生成器
    private final SnowflakeIdGenerator snowflakeIdGenerator = SnowflakeIdGenerator.getInstance();

    private static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }
    }

    /**
     * 处理私聊消息完整流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPrivateMessage(ChatMessage chatMessage, String conversationId) {
        String fromUserId = chatMessage.getFromId();
        String toUserId = chatMessage.getToId();
        String clientSeq = chatMessage.getClientSeq();

        log.info("处理私聊消息 - 发送方: {}, 接收方: {}, 客户端序列号: {}",
                fromUserId, toUserId, clientSeq);

        // 幂等性检查
        if (isIdempotentMessage(clientSeq)) {
            return;
        }

        try {
            // 1. 权限校验
            validateUserPermissions(fromUserId, toUserId);

            // 2. 业务校验通过后，生成全局唯一的消息ID
            String msgId = snowflakeIdGenerator.nextIdString();
            log.info("生成消息ID - 会话ID: {}, 消息ID: {}, 发送方: {}, 接收方: {}", 
                    conversationId, msgId, fromUserId, toUserId);
            
            // 构建包含新消息ID的ChatMessage
            chatMessage = ChatMessage.newBuilder(chatMessage)
                    .setUid(msgId)
                    .build();

            // 2. 消息主体持久化
            persistMessage(chatMessage, msgId, conversationId);
            
            // 3. 【关键】为发送方生成userSeq并存入user_msg_list (原子性)
            Long senderUserSeq = messageReceiverService.processSingleReceiver(fromUserId, msgId, conversationId);
            
            // 4. 【关键】为接收方生成userSeq并存入user_msg_list (原子性)  
            Long receiverUserSeq = messageReceiverService.processSingleReceiver(toUserId, msgId, conversationId);

            // 5. 会话处理
            handleConversation(conversationId, fromUserId, toUserId);
            
            // 6. 消息推送
            deliverMessage(chatMessage, msgId, receiverUserSeq, toUserId);
            
            // 7. 缓存更新 - 私聊使用各自的userSeq
            updateCache(chatMessage, msgId, senderUserSeq, receiverUserSeq, fromUserId, toUserId);

            // 8. 幂等性记录
            recordIdempotentIfNeeded(clientSeq, msgId, senderUserSeq);

            // 9. 发送消息发送确认回执给发送方（事务提交后异步执行）
            sendReceiptToSenderAsync(chatMessage, msgId, senderUserSeq);

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
     * 检查消息是否为重复消息
     */
    private boolean isIdempotentMessage(String clientSeq) {
        if (clientSeq != null && !clientSeq.trim().isEmpty()) {
            MessageIdempotentService.IdempotentResult idempotentResult =
                    messageIdempotentService.checkIdempotent(clientSeq);
            if (idempotentResult != null) {
                log.info("检测到重复私聊消息，忽略 - 客户端序列号: {}, 消息ID: {}",
                        clientSeq, idempotentResult.getMsgId());
                return true;
            }
        }
        return false;
    }

    /**
     * 记录幂等性信息
     */
    private void recordIdempotentIfNeeded(String clientSeq, String msgId, Long senderUserSeq) {
        if (clientSeq != null && !clientSeq.trim().isEmpty()) {
            messageIdempotentService.recordIdempotent(clientSeq, msgId, senderUserSeq);
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
     * 处理会话相关逻辑：创建会话、激活用户会话列表
     * 注意：未读数通过user_conversation_list表的unread_count字段管理，在MessageReceiverService中处理
     */
    private void handleConversation(String conversationId, String fromUserId, String toUserId) {
        conversationService.handleConversation(conversationId, fromUserId, toUserId);
        conversationService.activateUserConversationList(fromUserId, conversationId);
        conversationService.activateUserConversationList(toUserId, conversationId);
        // 未读数在MessageReceiverService.processSingleReceiver中通过数据库更新，不使用Redis缓存
    }

    /**
     * 推送消息给接收方
     */
    private void deliverMessage(ChatMessage chatMessage, String msgId, Long receiverUserSeq, String toUserId) {
        pushToReceiver(chatMessage, msgId, receiverUserSeq, toUserId);
        log.debug("私聊消息推送完成 - 推送给接收方: {}", toUserId);
    }

    /**
     * 推送消息给在线接收方
     */
    private void pushToReceiver(ChatMessage chatMessage, String msgId, Long userSeq, String toUserId) {
        UserSession session = onlineStatusService.getUserOnlineStatus(toUserId);
        if (session != null && session.getNodeId() != null) {
            ChatMessage message = buildEnrichedMessage(chatMessage, msgId, userSeq);
            gatewayMessagePushService.pushMessageToGateway(message, userSeq, session.getNodeId());
        }
    }

    /**
     * 构建包含服务端信息的消息
     */
    private ChatMessage buildEnrichedMessage(ChatMessage original, String msgId, Long userSeq) {
        return original.toBuilder()
                .setUid(msgId)
                .setUserSeq(userSeq != null ? userSeq : 0L)
                .build();
    }

    /**
     * 更新Redis缓存
     */
    private void updateCache(ChatMessage chatMessage, String msgId, Long senderUserSeq, Long receiverUserSeq,
                           String fromUserId, String toUserId) {
        String conversationId = generateConversationId(fromUserId, toUserId);
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_PRIVATE);
        String messageJson = MessageConverter.toJson(message);
        
        redisService.cacheMessage(msgId, messageJson, RedisKeyConstants.MESSAGE_CACHE_TTL_SECONDS);
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

    /**
     * 异步发送消息确认回执给发送方
     */
    private void sendReceiptToSenderAsync(ChatMessage originalMessage, String serverMsgId, Long senderUserSeq) {
        try {
            long serverTimestamp = System.currentTimeMillis();
            messageSendReceiptService.sendReceiptToSender(originalMessage, serverMsgId,
                                                        senderUserSeq, serverTimestamp);
        } catch (Exception e) {
            log.error("异步发送私聊消息回执失败 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}",
                     originalMessage.getFromId(), originalMessage.getClientSeq(), serverMsgId, e);
        }
    }
}