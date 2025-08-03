package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.Conversation;
import com.vanky.im.message.mapper.ConversationMapper;
import com.vanky.im.message.service.ConversationService;
import com.vanky.im.message.service.RedisService;
import com.vanky.im.message.service.UserConversationListService;
import com.vanky.im.message.service.GroupMemberService;
import com.vanky.im.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

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

    @Autowired
    private GroupMemberService groupMemberService;

    private final SnowflakeIdGenerator snowflakeIdGenerator = SnowflakeIdGenerator.getInstance();

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

    @Override
    @Transactional
    public String createGroupConversation(String conversationName, String conversationDesc,
                                        String creatorId, List<String> members) {
        try {
            log.info("开始创建群聊会话 - 创建者: {}, 群聊名称: {}, 成员数量: {}",
                    creatorId, conversationName, members.size());

            // 1. 生成群聊ID
            String groupId = snowflakeIdGenerator.nextIdString();
            String conversationId = "group_" + groupId;

            Date now = new Date();

            // 2. 创建conversation记录
            Conversation conversation = new Conversation();
            conversation.setConversationId(conversationId);
            conversation.setType(1); // 1-群聊 (根据数据库字段注释)
            conversation.setMemberCount(members.size());
            conversation.setLastMsgTime(now);
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversation.setCreateBy(creatorId);
            conversation.setUpdateBy(creatorId);
            this.save(conversation);

            log.info("群聊会话记录创建成功 - 会话ID: {}", conversationId);

            // 3. 为所有成员创建user_conversation_list记录
            for (String memberId : members) {
                userConversationListService.createUserConversationRecord(memberId, conversationId);
                log.debug("为成员创建会话记录 - 成员ID: {}, 会话ID: {}", memberId, conversationId);
            }

            // 4. 在Redis中管理群组成员关系
            for (String memberId : members) {
                groupMemberService.addGroupMember(groupId, memberId);
            }

            log.info("群聊创建完成 - 会话ID: {}, 群聊名称: {}, 成员数量: {}",
                    conversationId, conversationName, members.size());

            return conversationId;

        } catch (Exception e) {
            log.error("创建群聊会话失败 - 创建者: {}, 群聊名称: {}", creatorId, conversationName, e);
            throw new RuntimeException("创建群聊失败: " + e.getMessage(), e);
        }
    }
}