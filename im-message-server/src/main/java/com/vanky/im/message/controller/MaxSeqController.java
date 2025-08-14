package com.vanky.im.message.controller;

import com.vanky.im.message.service.ConversationMsgListService;
import com.vanky.im.message.service.UserMsgListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 最大序列号查询控制器
 * 为序列号服务提供查询业务消息表中最大序列号的REST接口
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Slf4j
@RestController
@RequestMapping("/api/message/max-seq")
public class MaxSeqController {

    @Autowired
    private UserMsgListService userMsgListService;

    @Autowired
    private ConversationMsgListService conversationMsgListService;

    /**
     * 查询用户最大序列号
     * 
     * @param userId 用户ID
     * @return 最大序列号响应
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserMaxSeq(@PathVariable String userId) {
        try {
            log.debug("查询用户最大序列号 - 用户ID: {}", userId);
            
            Long maxSeq = userMsgListService.getMaxSeqByUserId(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("maxSeq", maxSeq);
            response.put("businessKey", "user_" + userId);
            
            log.debug("用户最大序列号查询成功 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("查询用户最大序列号失败 - 用户ID: {}", userId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("maxSeq", 0L);
            response.put("errorMessage", "查询失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 查询会话最大序列号
     * 
     * @param conversationId 会话ID
     * @return 最大序列号响应
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversationMaxSeq(@PathVariable String conversationId) {
        try {
            log.debug("查询会话最大序列号 - 会话ID: {}", conversationId);
            
            Long maxSeq = conversationMsgListService.getMaxSeqByConversationId(conversationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("maxSeq", maxSeq);
            response.put("businessKey", "conversation_" + conversationId);
            
            log.debug("会话最大序列号查询成功 - 会话ID: {}, 最大序列号: {}", conversationId, maxSeq);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("查询会话最大序列号失败 - 会话ID: {}", conversationId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("maxSeq", 0L);
            response.put("errorMessage", "查询失败: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "max-seq-query");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
