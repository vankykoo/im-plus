package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.Conversation;

/**
* @author vanky
* @description 针对表【conversation】的数据库操作Service
* @createDate 2025-06-06
*/
public interface ConversationService extends IService<Conversation> {

    /**
     * 根据会话ID获取会话信息
     * @param conversationId 会话ID
     * @return 会话信息
     */
    Conversation getByConversationId(String conversationId);

    /**
     * 处理会话信息（创建或更新）
     * @param conversationId 会话ID
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     */
    void handleConversation(String conversationId, String fromUserId, String toUserId);
    
    /**
     * 处理群聊会话信息（创建或更新）
     * @param conversationId 会话ID
     * @param fromUserId 发送方用户ID
     * @param groupId 群组ID
     * @param memberCount 群组成员数量
     */
    void handleGroupConversation(String conversationId, String fromUserId, String groupId, int memberCount);
}