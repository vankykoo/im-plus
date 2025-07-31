package com.vanky.im.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 会话概览同步响应
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话概览同步响应模型，返回用户的会话列表概览;
// }}
// {{START MODIFICATIONS}}
@Data
public class SyncConversationResponse {
    
    /**
     * 响应状态码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 会话概览列表
     */
    private List<ConversationOverviewDTO> conversations;
    
    /**
     * 总会话数
     */
    private Integer totalCount;
    
    /**
     * 总未读数
     */
    private Integer totalUnreadCount;
    
    /**
     * 同步时间戳
     */
    private Long syncTimestamp;
    
    /**
     * 成功响应
     */
    public static SyncConversationResponse success(List<ConversationOverviewDTO> conversations) {
        SyncConversationResponse response = new SyncConversationResponse();
        response.setCode(200);
        response.setMessage("同步成功");
        response.setConversations(conversations);
        response.setTotalCount(conversations.size());
        response.setTotalUnreadCount(conversations.stream()
                .mapToInt(ConversationOverviewDTO::getUnreadCount)
                .sum());
        response.setSyncTimestamp(System.currentTimeMillis());
        return response;
    }
    
    /**
     * 失败响应
     */
    public static SyncConversationResponse error(String message) {
        SyncConversationResponse response = new SyncConversationResponse();
        response.setCode(500);
        response.setMessage(message);
        response.setSyncTimestamp(System.currentTimeMillis());
        return response;
    }
}
// {{END MODIFICATIONS}}
