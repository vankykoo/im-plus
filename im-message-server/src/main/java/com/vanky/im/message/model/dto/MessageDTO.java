package com.vanky.im.message.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 消息DTO
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDTO {
    
    /**
     * 消息类型
     */
    private Integer type;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 发送方ID
     */
    private String fromId;
    
    /**
     * 接收方ID
     */
    private String toId;
    
    /**
     * 消息唯一ID
     */
    private String uid;
    
    /**
     * 序列号
     */
    private String seq;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 重试次数
     */
    private Integer retry;
    
    /**
     * 消息状态
     */
    private Integer status;
} 