package com.vanky.im.message.processor;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.protocol.ReadReceipt;
import com.vanky.im.message.service.ReadNotificationService;
import com.vanky.im.message.service.UserConversationListService;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.service.ConversationService;
import com.vanky.im.message.service.GroupMemberService;
import com.vanky.im.message.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息已读回执处理器
 * 处理客户端上报的已读状态，更新数据库和缓存，发送已读通知
 * 
 * 核心功能：
 * 1. 私聊已读：更新message表status为已读，更新user_conversation_list
 * 2. 群聊已读：更新Redis已读计数，更新user_conversation_list
 * 3. 发送已读通知给消息发送方
 * 
 * @author vanky
 * @since 2025-08-12
 */
@Slf4j
@Component
public class ReadReceiptProcessor {

    @Autowired
    private MessageService messageService;
    
    @Autowired
    private UserConversationListService userConversationListService;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private GroupMemberService groupMemberService;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private ReadNotificationService readNotificationService;

    /**
     * 处理已读回执消息
     * 
     * @param chatMessage 包含已读回执的消息
     * @param userId 发送已读回执的用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void processReadReceipt(ChatMessage chatMessage, String userId) {
        if (!chatMessage.hasReadReceipt()) {
            log.warn("消息不包含已读回执信息 - 用户: {}", userId);
            return;
        }

        ReadReceipt readReceipt = chatMessage.getReadReceipt();
        String conversationId = readReceipt.getConversationId();
        long lastReadSeq = readReceipt.getLastReadSeq();

        log.info("处理已读回执 - 用户: {}, 会话: {}, 已读序列号: {}", userId, conversationId, lastReadSeq);

        try {
            // 判断是私聊还是群聊
            if (conversationId.startsWith("private_")) {
                processPrivateReadReceipt(userId, conversationId, lastReadSeq);
            } else if (conversationId.startsWith("group_")) {
                processGroupReadReceipt(userId, conversationId, lastReadSeq);
            } else {
                log.warn("未知的会话类型 - 会话ID: {}", conversationId);
            }
        } catch (Exception e) {
            log.error("处理已读回执失败 - 用户: {}, 会话: {}, 错误: {}", userId, conversationId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 处理私聊已读回执（基于写扩散模式）
     * 
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param lastReadSeq 已读到的最大序列号
     */
    private void processPrivateReadReceipt(String userId, String conversationId, long lastReadSeq) {
        log.debug("处理私聊已读回执 - 用户: {}, 会话: {}, 已读seq: {}", userId, conversationId, lastReadSeq);

        // 1. 更新用户会话列表，清零未读数，更新已读序列号
        userConversationListService.updateUserReadStatus(userId, conversationId, lastReadSeq);

        // 2. 获取用户之前的已读序列号
        Long previousReadSeq = redisService.getUserLastReadSeq(userId, conversationId);
        if (previousReadSeq == null) {
            previousReadSeq = 0L;
        }

        // 3. 如果没有新的已读消息，直接返回
        if (lastReadSeq <= previousReadSeq) {
            log.debug("没有新的已读消息 - 用户: {}, 当前seq: {}, 之前seq: {}", userId, lastReadSeq, previousReadSeq);
            return;
        }

        // 4. 更新Redis中的用户已读序列号
        redisService.setUserLastReadSeq(userId, conversationId, lastReadSeq);

        // 5. 更新消息状态为已读（只更新用户作为接收方的消息）
        int updatedCount = messageService.updateMessagesReadStatus(conversationId, userId, previousReadSeq + 1, lastReadSeq);
        log.debug("更新私聊消息已读状态 - 用户: {}, 会话: {}, 更新数量: {}", userId, conversationId, updatedCount);

        // 6. 发送已读通知给消息发送方
        readNotificationService.sendPrivateReadNotification(conversationId, userId, lastReadSeq);
    }

    /**
     * 处理群聊已读回执（基于读扩散模式）
     * 
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param lastReadSeq 已读到的最大序列号
     */
    private void processGroupReadReceipt(String userId, String conversationId, long lastReadSeq) {
        log.debug("处理群聊已读回执 - 用户: {}, 会话: {}, 已读seq: {}", userId, conversationId, lastReadSeq);

        // 1. 验证用户是否为群成员
        String groupId = conversationId.substring("group_".length());
        if (!groupMemberService.isGroupMember(groupId, userId)) {
            log.warn("用户不是群成员，忽略已读回执 - 用户: {}, 群组: {}", userId, groupId);
            return;
        }

        // 2. 更新用户会话列表，清零未读数，更新已读序列号
        userConversationListService.updateUserReadStatus(userId, conversationId, lastReadSeq);

        // 3. 获取用户之前的已读序列号
        Long previousReadSeq = redisService.getUserLastReadSeq(userId, conversationId);
        if (previousReadSeq == null) {
            previousReadSeq = 0L;
        }

        // 4. 如果没有新的已读消息，直接返回
        if (lastReadSeq <= previousReadSeq) {
            log.debug("没有新的已读消息 - 用户: {}, 当前seq: {}, 之前seq: {}", userId, lastReadSeq, previousReadSeq);
            return;
        }

        // 5. 更新Redis中的用户已读序列号
        redisService.setUserLastReadSeq(userId, conversationId, lastReadSeq);

        // 6. 获取新已读的消息列表
        processGroupReadMessages(userId, conversationId, previousReadSeq + 1, lastReadSeq);
    }

    /**
     * 处理群聊新已读的消息
     * 
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param startSeq 开始序列号（包含）
     * @param endSeq 结束序列号（包含）
     */
    private void processGroupReadMessages(String userId, String conversationId, long startSeq, long endSeq) {
        // 获取指定序列号范围内的消息ID列表
        var messageIds = messageService.getGroupMessageIdsBySeqRange(conversationId, startSeq, endSeq);
        
        if (messageIds.isEmpty()) {
            log.debug("指定序列号范围内没有消息 - 会话: {}, 范围: [{}, {}]", conversationId, startSeq, endSeq);
            return;
        }

        log.debug("处理群聊新已读消息 - 用户: {}, 会话: {}, 消息数量: {}", userId, conversationId, messageIds.size());

        // 判断是否为小群（支持已读用户列表）
        String groupId = conversationId.substring("group_".length());
        boolean isSmallGroup = groupMemberService.isSmallGroup(groupId);

        // 批量更新Redis已读计数
        for (String msgId : messageIds) {
            // 原子增加已读计数
            redisService.incrementGroupReadCount(msgId);
            
            // 小群才维护已读用户列表
            if (isSmallGroup) {
                redisService.addGroupReadUser(msgId, userId);
            }
        }

        // 发送已读通知给消息发送方
        readNotificationService.sendGroupReadNotifications(conversationId, messageIds, userId);
    }
}
