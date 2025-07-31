package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.UserConversationList;

/**
* @author vanky
* @description 针对表【user_conversation_list】的数据库操作Service
* @createDate 2025-06-06
*/
public interface UserConversationListService extends IService<UserConversationList> {

    /**
     * 根据用户ID和会话ID获取用户会话记录
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return 用户会话记录
     */
    UserConversationList getByUserIdAndConversationId(Long userId, String conversationId);

    /**
     * 创建用户会话记录
     * @param userId 用户ID
     * @param conversationId 会话ID
     */
    void createUserConversationRecord(String userId, String conversationId);

    /**
     * 更新用户会话列表
     * @param userId 用户ID
     * @param conversationId 会话ID
     */
    void updateUserConversationList(Long userId, String conversationId);

    /**
     * 更新用户会话列表的消息相关信息
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param msgId 最新消息ID
     */
    void updateUserConversationMessage(Long userId, String conversationId, String msgId);
}