package com.vanky.im.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vanky.im.message.entity.ConversationMsgList;

import java.util.List;

/**
* @author vanky
* @description 针对表【conversation_msg_list】的数据库操作Service
* @createDate 2025-06-06
*/
public interface ConversationMsgListService extends IService<ConversationMsgList> {

    /**
     * 获取指定会话中seq大于指定值的消息列表（读扩散模式）
     *
     * @param conversationId 会话ID
     * @param afterSeq 起始seq（不包含）
     * @param limit 最大返回数量
     * @return 消息列表，按seq升序排列
     */
    List<ConversationMsgList> getMessagesAfterSeq(String conversationId, Long afterSeq, Integer limit);

    /**
     * 获取指定会话的最大seq
     *
     * @param conversationId 会话ID
     * @return 最大seq，如果会话不存在返回0
     */
    Long getMaxSeq(String conversationId);
}