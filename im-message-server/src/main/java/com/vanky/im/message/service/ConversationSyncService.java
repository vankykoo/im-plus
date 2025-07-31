package com.vanky.im.message.service;

import com.vanky.im.message.dto.ConversationOverviewDTO;
import com.vanky.im.message.dto.SyncConversationRequest;

import java.util.List;

/**
 * 会话同步服务接口
 * 负责处理会话概览同步相关功能
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话同步服务接口，提供会话概览同步功能;
// }}
// {{START MODIFICATIONS}}
public interface ConversationSyncService {
    
    /**
     * 同步用户的会话概览列表
     * 
     * @param request 同步请求
     * @return 会话概览列表
     */
    List<ConversationOverviewDTO> syncUserConversations(SyncConversationRequest request);
    
    /**
     * 获取用户的会话概览列表（高效JOIN查询）
     * 
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 会话概览列表
     */
    List<ConversationOverviewDTO> getUserConversationOverviews(Long userId, Integer limit);
}
// {{END MODIFICATIONS}}
