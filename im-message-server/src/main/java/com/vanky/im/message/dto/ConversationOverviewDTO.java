package com.vanky.im.message.dto;

import lombok.Data;

import java.util.Date;

/**
 * 会话概览DTO
 * 包含渲染会话列表所需的所有信息
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话概览DTO，包含会话列表渲染所需的所有信息;
// }}
// {{START MODIFICATIONS}}
@Data
public class ConversationOverviewDTO {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 未读消息数
     */
    private Integer unreadCount;
    
    /**
     * 会话最后更新时间（用于排序）
     */
    private Date lastUpdateTime;
    
    /**
     * 最后一条消息内容
     */
    private String lastMsgContent;
    
    /**
     * 最后一条消息类型
     */
    private Byte lastMsgContentType;
    
    /**
     * 最后一条消息发送者昵称
     */
    private String lastMsgSender;
    
    /**
     * 会话类型（0-私聊，1-群聊）
     */
    private Integer conversationType;
    
    /**
     * 会话名称
     */
    private String conversationName;
    
    /**
     * 会话头像
     */
    private String conversationAvatar;
    
    /**
     * 最后一条消息时间
     */
    private Date lastMsgTime;
    
    /**
     * 最后一条消息ID
     */
    private Long lastMsgId;
}
// {{END MODIFICATIONS}}
