package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.UserConversationList;
import com.vanky.im.message.mapper.UserConversationListMapper;
import com.vanky.im.message.service.UserConversationListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author vanky
* @description 针对表【user_conversation_list】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Slf4j
@Service
public class UserConversationListServiceImpl extends ServiceImpl<UserConversationListMapper, UserConversationList>
    implements UserConversationListService {

    @Override
    public UserConversationList getByUserIdAndConversationId(Long userId, String conversationId) {
        return this.lambdaQuery()
                .eq(UserConversationList::getUserId, userId)
                .eq(UserConversationList::getConversationId, conversationId)
                .one();
    }

    @Override
    public void createUserConversationRecord(String userId, String conversationId) {
        Date now = new Date();
        UserConversationList userConversation = new UserConversationList();
        userConversation.setUserId(Long.valueOf(userId));
        userConversation.setConversationId(conversationId);
        userConversation.setLastReadSeq(0L); // 初始未读
        userConversation.setCreateTime(now);
        userConversation.setUpdateTime(now);
        this.save(userConversation);
        
        log.debug("创建用户会话记录 - 用户ID: {}, 会话ID: {}", userId, conversationId);
    }

    @Override
    public void updateUserConversationList(Long userId, String conversationId) {
        UserConversationList userConversation = this.getByUserIdAndConversationId(userId, conversationId);
        if (userConversation != null) {
            userConversation.setUpdateTime(new Date());
            this.updateById(userConversation);
        }
    }
}