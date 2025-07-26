package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.Conversation;
import com.vanky.im.message.mapper.ConversationMapper;
import com.vanky.im.message.service.ConversationService;
import com.vanky.im.message.service.RedisService;
import com.vanky.im.message.service.UserConversationListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author vanky
* @description 针对表【conversation】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Slf4j
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
    implements ConversationService {

    @Autowired
    private UserConversationListService userConversationListService;

    @Autowired
    private RedisService redisService;

    @Override
    public Conversation getByConversationId(String conversationId) {
        return this.lambdaQuery()
                .eq(Conversation::getConversationId, conversationId)
                .one();
    }

    @Override
    public void handleConversation(String conversationId, String fromUserId, String toUserId) {
        Date now = new Date();
        Conversation existingConversation = this.getByConversationId(conversationId);
        
        if (existingConversation == null) {
            // 首次创建会话
            log.info("创建新会话 - 会话ID: {}", conversationId);
            
            // 创建conversation记录
            Conversation conversation = new Conversation();
            conversation.setConversationId(conversationId);
            conversation.setType(1); // 1-私聊
            conversation.setMemberCount(2);
            conversation.setLastMsgTime(now);
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversation.setCreateBy(fromUserId);
            conversation.setUpdateBy(fromUserId);
            this.save(conversation);
            
            // 为发送方和接收方创建user_conversation_list记录
            userConversationListService.createUserConversationRecord(fromUserId, conversationId);
            userConversationListService.createUserConversationRecord(toUserId, conversationId);
            
        } else {
            // 更新现有会话
            existingConversation.setLastMsgTime(now);
            existingConversation.setUpdateTime(now);
            existingConversation.setUpdateBy(fromUserId);
            this.updateById(existingConversation);
            
            // 更新用户会话列表（标记为活跃）
            userConversationListService.updateUserConversationList(Long.valueOf(fromUserId), conversationId);
            userConversationListService.updateUserConversationList(Long.valueOf(toUserId), conversationId);
        }
        
        log.debug("会话信息处理完成 - 会话ID: {}", conversationId);
    }
    
    @Override
    public void handleGroupConversation(String conversationId, String fromUserId, String groupId, int memberCount) {
        Date now = new Date();
        Conversation existingConversation = this.getByConversationId(conversationId);
        
        if (existingConversation == null) {
            // 首次创建群聊会话
            log.info("创建新群聊会话 - 会话ID: {}, 群组ID: {}", conversationId, groupId);
            
            // 创建conversation记录
            Conversation conversation = new Conversation();
            conversation.setConversationId(conversationId);
            conversation.setType(2); // 2-群聊
            conversation.setMemberCount(memberCount);
            conversation.setLastMsgTime(now);
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversation.setCreateBy(fromUserId);
            conversation.setUpdateBy(fromUserId);
            this.save(conversation);
        } else {
            // 更新现有群聊会话
            existingConversation.setLastMsgTime(now);
            existingConversation.setUpdateTime(now);
            existingConversation.setUpdateBy(fromUserId);
            existingConversation.setMemberCount(memberCount); // 更新成员数量
            this.updateById(existingConversation);
        }
        
        log.debug("群聊会话信息处理完成 - 会话ID: {}, 群组ID: {}, 成员数: {}", conversationId, groupId, memberCount);
    }

    @Override
    public void updateConversationLatestMessage(String conversationId, String latestMsgId,
                                              String latestMsgContent, long latestMsgTime, String fromUserId) {
        try {
            // 1. 更新数据库中的会话最新消息时间
            Conversation conversation = this.getByConversationId(conversationId);
            if (conversation != null) {
                conversation.setLastMsgTime(new Date(latestMsgTime));
                conversation.setUpdateTime(new Date());
                conversation.setUpdateBy(fromUserId);
                this.updateById(conversation);
            }

            // 2. 更新Redis缓存中的会话最新消息信息
            redisService.updateConversationLatestMsg(conversationId, latestMsgId, latestMsgContent, latestMsgTime);

            log.debug("更新会话最新消息完成 - 会话ID: {}, 消息ID: {}, 时间: {}",
                    conversationId, latestMsgId, latestMsgTime);

        } catch (Exception e) {
            log.error("更新会话最新消息失败 - 会话ID: {}, 消息ID: {}", conversationId, latestMsgId, e);
        }
    }

    @Override
    public void activateUserConversationList(String userId, String conversationId) {
        try {
            long currentTime = System.currentTimeMillis();

            // 1. 更新数据库中的用户会话列表
            userConversationListService.updateUserConversationList(Long.valueOf(userId), conversationId);

            // 2. 激活Redis中的用户会话列表排序
            redisService.activateUserConversation(userId, conversationId, currentTime);

            log.debug("激活用户会话列表完成 - 用户ID: {}, 会话ID: {}", userId, conversationId);

        } catch (Exception e) {
            log.error("激活用户会话列表失败 - 用户ID: {}, 会话ID: {}", userId, conversationId, e);
        }
    }
}