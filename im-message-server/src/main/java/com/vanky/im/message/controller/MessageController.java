package com.vanky.im.message.controller;

import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.message.model.dto.MessagePullRequest;
import com.vanky.im.message.model.dto.MessagePullResponse;
import com.vanky.im.message.service.MessageQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 消息控制器，提供消息拉取接口
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageQueryService messageQueryService;

    /**
     * 拉取消息接口
     * @param request 消息拉取请求
     * @return 消息拉取响应
     */
    @PostMapping("/pull")
    public ApiResponse<MessagePullResponse> pullMessages(@Validated @RequestBody MessagePullRequest request) {
        log.info("接收消息拉取请求 - 会话ID: {}, 起始序列号: {}, 结束序列号: {}, 限制: {}", 
                request.getConversationId(), 
                request.getStartSeq(), 
                request.getEndSeq(), 
                request.getLimit());
        
        try {
            MessagePullResponse response = messageQueryService.queryMessages(
                    request.getConversationId(),
                    request.getStartSeq(),
                    request.getEndSeq(),
                    request.getLimit()
            );
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("拉取消息失败", e);
            return ApiResponse.error(500, "拉取消息失败: " + e.getMessage());
        }
    }

    /**
     * 拉取消息接口（GET方式）
     * 方便客户端直接发起HTTP GET请求
     * 
     * @param conversationId 会话ID
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     * @param limit 消息数量限制
     * @return 消息拉取响应
     */
    @GetMapping("/pull")
    public ApiResponse<MessagePullResponse> pullMessagesGet(
            @RequestParam("conversationId") String conversationId,
            @RequestParam("startSeq") Long startSeq,
            @RequestParam(value = "endSeq", required = false) Long endSeq,
            @RequestParam(value = "limit", required = false) Integer limit) {
        
        log.info("接收GET消息拉取请求 - 会话ID: {}, 起始序列号: {}, 结束序列号: {}, 限制: {}", 
                conversationId, startSeq, endSeq, limit);
        
        try {
            MessagePullResponse response = messageQueryService.queryMessages(
                    conversationId, startSeq, endSeq, limit);
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("GET拉取消息失败", e);
            return ApiResponse.error(500, "拉取消息失败: " + e.getMessage());
        }
    }
} 