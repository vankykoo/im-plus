package com.vanky.im.message.controller;

import com.vanky.im.message.model.request.PullGroupMessagesRequest;
import com.vanky.im.message.model.response.PullGroupMessagesResponse;
import com.vanky.im.message.service.GroupMessageSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群聊消息同步控制器
 * 用于读扩散模式下的群聊消息拉取
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 创建群聊消息同步控制器，提供读扩散模式的HTTP接口;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@RestController
@RequestMapping("/api/group-messages")
public class GroupMessageSyncController {
    
    @Autowired
    private GroupMessageSyncService groupMessageSyncService;
    
    /**
     * 拉取群聊消息（读扩散模式）
     * 
     * POST /api/group-messages/pull
     * 
     * 请求体示例：
     * {
     *   "userId": "123",
     *   "conversations": {
     *     "group_101": 480,
     *     "group_102": 1250
     *   },
     *   "limit": 100
     * }
     * 
     * @param request 拉取请求
     * @return 按会话分组的消息列表
     */
    @PostMapping("/pull")
    public ResponseEntity<PullGroupMessagesResponse> pullGroupMessages(
            @Validated @RequestBody PullGroupMessagesRequest request) {
        
        log.info("收到群聊消息拉取请求 - 用户ID: {}, 会话数量: {}", 
                request.getUserId(), request.getConversations().size());
        
        try {
            PullGroupMessagesResponse response = groupMessageSyncService.pullGroupMessages(request);
            
            log.info("群聊消息拉取成功 - 用户ID: {}, 总消息数: {}", 
                    request.getUserId(), response.getTotalCount());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("群聊消息拉取失败 - 用户ID: {}", request.getUserId(), e);
            
            // 返回空结果，不影响客户端
            PullGroupMessagesResponse errorResponse = new PullGroupMessagesResponse();
            errorResponse.setConversations(Map.of());
            errorResponse.setLatestSeqs(Map.of());
            errorResponse.setTotalCount(0);
            
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 获取指定会话的最新序列号
     * 
     * GET /api/group-messages/latest-seq/{conversationId}
     * 
     * @param conversationId 会话ID
     * @return 最新序列号
     */
    @GetMapping("/latest-seq/{conversationId}")
    public ResponseEntity<Map<String, Object>> getLatestSeq(@PathVariable String conversationId) {
        
        log.debug("获取会话最新seq - 会话ID: {}", conversationId);
        
        try {
            Long latestSeq = groupMessageSyncService.getLatestSeq(conversationId);
            
            Map<String, Object> result = Map.of(
                "conversationId", conversationId,
                "latestSeq", latestSeq,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("获取会话最新seq失败 - 会话ID: {}", conversationId, e);
            
            Map<String, Object> errorResult = Map.of(
                "conversationId", conversationId,
                "latestSeq", 0L,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(errorResult);
        }
    }
    
    /**
     * 批量获取多个会话的最新序列号
     * 
     * POST /api/group-messages/latest-seqs
     * 
     * 请求体示例：
     * {
     *   "conversationIds": ["group_101", "group_102", "group_103"]
     * }
     * 
     * @param requestBody 包含会话ID列表的请求体
     * @return 会话ID到最新序列号的映射
     */
    @PostMapping("/latest-seqs")
    public ResponseEntity<Map<String, Object>> getLatestSeqs(
            @RequestBody Map<String, List<String>> requestBody) {
        
        List<String> conversationIds = requestBody.get("conversationIds");
        log.debug("批量获取会话最新seq - 会话数量: {}", conversationIds.size());
        
        try {
            Map<String, Long> latestSeqs = groupMessageSyncService.getLatestSeqs(conversationIds);
            
            Map<String, Object> result = Map.of(
                "latestSeqs", latestSeqs,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("批量获取会话最新seq失败", e);
            
            Map<String, Object> errorResult = Map.of(
                "latestSeqs", Map.of(),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(errorResult);
        }
    }
}
// {{END MODIFICATIONS}}
