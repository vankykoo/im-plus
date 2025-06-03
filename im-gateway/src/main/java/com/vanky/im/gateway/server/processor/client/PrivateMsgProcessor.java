package com.vanky.im.gateway.server.processor.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.entity.Conversation;
import com.vanky.im.gateway.entity.UserConversationList;
import com.vanky.im.gateway.mq.MessageQueueService;
import com.vanky.im.gateway.service.ConversationService;
import com.vanky.im.gateway.service.UserConversationListService;
import com.vanky.im.gateway.session.MsgSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author vanky
 * @create 2025/5/22 21:07
 * @description 私聊消息处理器
 */
@Slf4j
@Component
public class PrivateMsgProcessor {

    private static ConversationService conversationService;
    private static UserConversationListService userConversationListService;
    private static MessageQueueService messageQueueService;

    @Autowired
    public void setConversationService(ConversationService conversationService) {
        PrivateMsgProcessor.conversationService = conversationService;
    }

    @Autowired
    public void setUserConversationListService(UserConversationListService userConversationListService) {
        PrivateMsgProcessor.userConversationListService = userConversationListService;
    }
    
    @Autowired
    public void setMessageQueueService(MessageQueueService messageQueueService) {
        PrivateMsgProcessor.messageQueueService = messageQueueService;
    }

    public static void process(ChatMessage msg) {
        try {
            // 检查并创建会话
            String conversationId = checkAndCreateConversation(msg.getFromId(), msg.getToId());
            log.info("私聊消息处理 - 发送方: {}, 接收方: {}, 会话ID: {}", msg.getFromId(), msg.getToId(), conversationId);
            
            // 将消息投递到消息队列
            messageQueueService.sendMessage(conversationId, msg);
            log.info("消息已投递到消息队列 - 会话ID: {}, 消息ID: {}", conversationId, msg.getUid());
            
            // 发送消息给接收方
            MsgSender.sendMsg(msg.getToId(), msg);
        } catch (Exception e) {
            log.error("处理私聊消息时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查并创建会话
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     * @return 会话ID
     */
    private static String checkAndCreateConversation(String fromUserId, String toUserId) {
        try {
            // 生成会话ID（基于两个用户ID的组合，确保唯一性）
            String conversationId = generateConversationId(fromUserId, toUserId);
            
            // 检查会话是否已存在
            QueryWrapper<Conversation> conversationQuery = new QueryWrapper<>();
            conversationQuery.eq("conversation_id", conversationId);
            Conversation existingConversation = conversationService.getOne(conversationQuery);
            
            if (existingConversation == null) {
                log.info("创建新的私聊会话 - 会话ID: {}, 参与者: {} 和 {}", conversationId, fromUserId, toUserId);
                
                // 创建新会话
                Conversation newConversation = new Conversation();
                newConversation.setConversationId(conversationId);
                newConversation.setType(0); // 0表示私聊
                newConversation.setMemberCount(2); // 私聊固定为2人
                newConversation.setLastMsgTime(new Date());
                newConversation.setCreateTime(new Date());
                newConversation.setUpdateTime(new Date());
                newConversation.setCreateBy(fromUserId);
                newConversation.setUpdateBy(fromUserId);
                
                conversationService.save(newConversation);
                
                // 为两个用户创建会话关联
                createUserConversationList(Long.parseLong(fromUserId), conversationId);
                createUserConversationList(Long.parseLong(toUserId), conversationId);
                
                log.info("成功创建私聊会话和用户关联 - 会话ID: {}", conversationId);
            } else {
                log.debug("会话已存在 - 会话ID: {}", conversationId);
            }
            
            return conversationId;
        } catch (Exception e) {
            log.error("检查并创建会话时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("创建会话失败", e);
        }
    }

    /**
     * 生成会话ID
     * 对于私聊，使用两个用户ID的组合生成唯一的会话ID
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 会话ID
     */
    private static String generateConversationId(String userId1, String userId2) {
        // 确保会话ID的唯一性，无论用户ID的顺序如何
        long id1 = Long.parseLong(userId1);
        long id2 = Long.parseLong(userId2);
        
        // 使用较小的ID在前，较大的ID在后，确保同一对用户的会话ID始终相同
        if (id1 < id2) {
            return "private_" + id1 + "_" + id2;
        } else {
            return "private_" + id2 + "_" + id1;
        }
    }

    /**
     * 创建用户会话关联
     * @param userId 用户ID
     * @param conversationId 会话ID
     */
    private static void createUserConversationList(Long userId, String conversationId) {
        try {
            // 检查用户会话关联是否已存在
            QueryWrapper<UserConversationList> query = new QueryWrapper<>();
            query.eq("user_id", userId)
                 .eq("conversation_id", conversationId);
            
            UserConversationList existing = userConversationListService.getOne(query);
            if (existing == null) {
                UserConversationList userConversation = new UserConversationList();
                userConversation.setUserId(userId);
                userConversation.setConversationId(conversationId);
                userConversation.setLastReadSeq(0L);
                userConversation.setCreateTime(new Date());
                userConversation.setUpdateTime(new Date());
                
                userConversationListService.save(userConversation);
                log.debug("创建用户会话关联 - 用户ID: {}, 会话ID: {}", userId, conversationId);
            }
        } catch (Exception e) {
            log.error("创建用户会话关联时发生错误 - 用户ID: {}, 会话ID: {}, 错误: {}", userId, conversationId, e.getMessage(), e);
            throw new RuntimeException("创建用户会话关联失败", e);
        }
    }
}
