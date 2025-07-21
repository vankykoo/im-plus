package com.vanky.im.message.service;

import com.vanky.im.message.model.dto.MessageDTO;
import com.vanky.im.message.model.dto.MessagePullResponse;

import java.util.List;

/**
 * 消息查询服务接口
 */
public interface MessageQueryService {

    /**
     * 查询指定会话和序列号范围的消息
     * @param conversationId 会话ID
     * @param startSeq 起始序列号（包含）
     * @param endSeq 结束序列号（包含）
     * @param limit 消息数量限制
     * @return 消息拉取响应
     */
    MessagePullResponse queryMessages(String conversationId, Long startSeq, Long endSeq, Integer limit);

    /**
     * 从Redis缓存中查询消息
     * @param conversationId 会话ID
     * @param startSeq 起始序列号（包含）
     * @param endSeq 结束序列号（包含）
     * @return 消息列表
     */
    List<MessageDTO> queryMessagesFromCache(String conversationId, Long startSeq, Long endSeq);

    /**
     * 从数据库中查询消息
     * @param conversationId 会话ID
     * @param startSeq 起始序列号（包含）
     * @param endSeq 结束序列号（包含）
     * @param limit 消息数量限制
     * @return 消息列表
     */
    List<MessageDTO> queryMessagesFromDb(String conversationId, Long startSeq, Long endSeq, Integer limit);
} 