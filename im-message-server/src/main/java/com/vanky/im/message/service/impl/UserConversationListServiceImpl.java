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
        userConversation.setUnreadCount(0); // 初始未读数为0
        userConversation.setLastMsgId(null); // 初始无最新消息ID
        userConversation.setLastUpdateTime(now); // 初始更新时间
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

    @Override
    public void updateUserConversationMessage(Long userId, String conversationId, String msgId) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-07-28 23:08:31 +08:00; Reason: 实现用户会话列表的消息相关信息更新;
        // }}
        // {{START MODIFICATIONS}}
        try {
            UserConversationList userConversation = this.getByUserIdAndConversationId(userId, conversationId);
            if (userConversation != null) {
                Date now = new Date();

                // 更新未读数 +1
                Integer currentUnreadCount = userConversation.getUnreadCount();
                if (currentUnreadCount == null) {
                    currentUnreadCount = 0;
                }
                userConversation.setUnreadCount(currentUnreadCount + 1);

                // 更新最新消息ID
                userConversation.setLastMsgId(Long.valueOf(msgId));

                // 更新最后更新时间
                userConversation.setLastUpdateTime(now);
                userConversation.setUpdateTime(now);

                this.updateById(userConversation);

                log.debug("更新用户会话消息信息完成 - 用户ID: {}, 会话ID: {}, 消息ID: {}, 新未读数: {}",
                        userId, conversationId, msgId, userConversation.getUnreadCount());
            } else {
                log.warn("用户会话记录不存在 - 用户ID: {}, 会话ID: {}", userId, conversationId);
            }
        } catch (Exception e) {
            log.error("更新用户会话消息信息失败 - 用户ID: {}, 会话ID: {}, 消息ID: {}",
                    userId, conversationId, msgId, e);
            throw new RuntimeException("更新用户会话消息信息失败", e);
        }
        // {{END MODIFICATIONS}}
    }
}