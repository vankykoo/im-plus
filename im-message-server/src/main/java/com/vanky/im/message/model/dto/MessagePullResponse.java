package com.vanky.im.message.model.dto;

import lombok.Data;
import java.util.List;

/**
 * 消息拉取响应DTO
 */
@Data
public class MessagePullResponse {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 消息列表
     */
    private List<MessageDTO> messages;
    
    /**
     * 起始序列号
     */
    private Long startSeq;
    
    /**
     * 结束序列号
     */
    private Long endSeq;
    
    /**
     * 消息数量
     */
    private Integer count;
    
    /**
     * 是否有更多消息
     */
    private Boolean hasMore;
} 