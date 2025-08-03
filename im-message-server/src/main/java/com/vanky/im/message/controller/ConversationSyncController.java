package com.vanky.im.message.controller;

import com.vanky.im.message.dto.ConversationOverviewDTO;
import com.vanky.im.message.dto.SyncConversationRequest;
import com.vanky.im.message.dto.SyncConversationResponse;
import com.vanky.im.message.dto.CreateGroupRequest;
import com.vanky.im.message.dto.CreateGroupResponse;
import com.vanky.im.message.service.ConversationSyncService;
import com.vanky.im.message.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


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

    @Autowired
    private ConversationService conversationService;
    
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

    /**
     * 创建群聊会话
     * 客户端调用此接口创建新的群聊
     *
     * @param request 创建群聊请求
     * @return 创建群聊响应
     */
    @PostMapping("/create")
    public CreateGroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        log.info("收到创建群聊请求 - 创建者: {}, 群聊名称: {}, 成员数量: {}",
                request.getCreatorId(), request.getConversationName(), request.getMemberCount());

        try {
            // 验证请求参数
            if (!request.isGroupConversation()) {
                log.warn("创建群聊失败 - 会话类型不正确: {}", request.getConversationType());
                return CreateGroupResponse.badRequest("会话类型必须为GROUP");
            }

            if (request.getMemberCount() < 2) {
                log.warn("创建群聊失败 - 成员数量不足: {}", request.getMemberCount());
                return CreateGroupResponse.badRequest("群聊至少需要2个成员");
            }

            // 调用服务层创建群聊
            String conversationId = conversationService.createGroupConversation(
                    request.getConversationName(),
                    request.getConversationDesc(),
                    request.getCreatorId(),
                    request.getMembers()
            );

            log.info("群聊创建成功 - 会话ID: {}, 群聊名称: {}, 成员数量: {}",
                    conversationId, request.getConversationName(), request.getMemberCount());

            return CreateGroupResponse.success(
                    conversationId,
                    request.getConversationName(),
                    request.getConversationDesc(),
                    request.getMemberCount()
            );

        } catch (Exception e) {
            log.error("创建群聊失败 - 创建者: {}, 群聊名称: {}",
                    request.getCreatorId(), request.getConversationName(), e);
            return CreateGroupResponse.error("创建群聊失败: " + e.getMessage());
        }
    }
}
// {{END MODIFICATIONS}}
