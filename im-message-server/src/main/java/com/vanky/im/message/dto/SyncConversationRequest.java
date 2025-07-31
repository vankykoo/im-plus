package com.vanky.im.message.dto;

import lombok.Data;



/**
 * 会话概览同步请求
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话概览同步请求模型，用于用户登录后快速获取会话列表;
// }}
// {{START MODIFICATIONS}}
@Data
public class SyncConversationRequest {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 限制返回的会话数量（可选，默认100）
     */
    private Integer limit = 100;
    
    /**
     * 是否包含已删除的会话（可选，默认false）
     */
    private Boolean includeDeleted = false;
}
// {{END MODIFICATIONS}}
