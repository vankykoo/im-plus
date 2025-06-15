package com.vanky.im.server.service;

import com.vanky.im.server.entity.ConversationMsgList;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 86180
* @description 针对表【conversation_msg_list】的数据库操作Service
* @createDate 2025-06-03 21:43:35
*/
public interface ConversationMsgListService extends IService<ConversationMsgList> {

    /**
     * 获取指定会话的下一个序列号
     * @param conversationId 会话ID
     * @return 下一个序列号
     */
    Long getNextSeq(String conversationId);

    /**
     * 添加消息到会话消息链
     * @param conversationId 会话ID
     * @param msgId 消息ID
     * @param seq 序列号
     * @return 是否添加成功
     */
    boolean addMessageToConversation(String conversationId, Long msgId, Long seq);
}
