package com.vanky.im.message.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 消息拉取请求DTO
 */
@Data
public class MessagePullRequest {
    
    /**
     * 会话ID
     */
    @NotBlank(message = "会话ID不能为空")
    private String conversationId;
    
    /**
     * 起始序列号（包含）
     */
    @NotNull(message = "起始序列号不能为空")
    private Long startSeq;
    
    /**
     * 结束序列号（包含，可选）
     */
    private Long endSeq;
    
    /**
     * 消息数量限制
     */
    private Integer limit;
}