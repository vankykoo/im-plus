package com.vanky.im.message.controller;

import com.vanky.im.message.dto.ConversationOverviewDTO;
import com.vanky.im.message.dto.SyncConversationRequest;
import com.vanky.im.message.dto.SyncConversationResponse;
import com.vanky.im.message.service.ConversationSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * 会话同步控制器
 * 提供会话概览同步相关的HTTP API
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话同步控制器，提供会话概览同步的HTTP API接口;
// }}
// {{START MODIFICATIONS}}
@RestController
@RequestMapping("/api/conversations")
public class ConversationSyncController {

    private static final Logger log = LoggerFactory.getLogger(ConversationSyncController.class);
    
    @Autowired
    private ConversationSyncService conversationSyncService;
    
    /**
     * 同步用户会话概览
     * 用户登录后调用此接口快速获取会话列表
     * 
     * @param request 同步请求
     * @return 会话概览响应
     */
    @PostMapping("/sync")
    public SyncConversationResponse syncConversations(@RequestBody SyncConversationRequest request) {
        log.info("收到会话概览同步请求 - 用户ID: {}", request.getUserId());
        
        try {
            List<ConversationOverviewDTO> conversations = conversationSyncService.syncUserConversations(request);
            
            log.info("会话概览同步成功 - 用户ID: {}, 会话数量: {}", 
                    request.getUserId(), conversations.size());
            
            return SyncConversationResponse.success(conversations);
            
        } catch (Exception e) {
            log.error("会话概览同步失败 - 用户ID: {}", request.getUserId(), e);
            return SyncConversationResponse.error("同步失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户会话概览（GET方式，便于测试）
     * 
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 会话概览响应
     */
    @GetMapping("/sync")
    public SyncConversationResponse syncConversationsGet(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit) {
        
        log.info("收到会话概览同步请求(GET) - 用户ID: {}, 限制: {}", userId, limit);
        
        try {
            SyncConversationRequest request = new SyncConversationRequest();
            request.setUserId(userId);
            request.setLimit(limit);
            
            List<ConversationOverviewDTO> conversations = conversationSyncService.syncUserConversations(request);
            
            log.info("会话概览同步成功(GET) - 用户ID: {}, 会话数量: {}", 
                    userId, conversations.size());
            
            return SyncConversationResponse.success(conversations);
            
        } catch (Exception e) {
            log.error("会话概览同步失败(GET) - 用户ID: {}", userId, e);
            return SyncConversationResponse.error("同步失败: " + e.getMessage());
        }
    }
}
// {{END MODIFICATIONS}}
