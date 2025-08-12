package com.vanky.im.message.controller;

import com.vanky.im.message.service.ReadNotificationService;
import com.vanky.im.message.service.GroupMemberService;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 消息已读功能相关API控制器
 * 
 * @author vanky
 * @since 2025-08-12
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageReadController {

    @Autowired
    private ReadNotificationService readNotificationService;
    
    @Autowired
    private GroupMemberService groupMemberService;
    
    @Autowired
    private MessageService messageService;

    /**
     * 获取群聊消息的已读成员列表
     * 
     * @param msgId 消息ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 已读成员列表
     */
    @GetMapping("/{msgId}/read-list")
    public ApiResponse<Map<String, Object>> getMessageReadList(
            @PathVariable String msgId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("查询消息已读列表 - 消息ID: {}, 页码: {}, 每页大小: {}", msgId, page, size);

        try {
            // 1. 参数验证
            if (msgId == null || msgId.trim().isEmpty()) {
                return ApiResponse.error("消息ID不能为空");
            }
            
            if (page < 1) {
                page = 1;
            }
            
            if (size < 1 || size > 100) {
                size = 20;
            }

            // 2. 获取消息信息，验证消息是否存在
            Map<String, String> msgSenderMap = messageService.getMessageSenders(List.of(msgId));
            if (msgSenderMap.isEmpty()) {
                return ApiResponse.error("消息不存在");
            }

            // 3. 验证是否为群聊消息（通过消息ID查询会话ID）
            String conversationId = messageService.getMessageConversationId(msgId);
            if (conversationId == null || !conversationId.startsWith("group_")) {
                return ApiResponse.error("只支持查询群聊消息的已读列表");
            }

            // 4. 提取群组ID并验证是否为小群
            String groupId = conversationId.substring("group_".length());
            if (!groupMemberService.isSmallGroup(groupId)) {
                return ApiResponse.error("大群不支持查看已读成员列表");
            }

            // 5. 获取已读用户列表
            List<String> readUserIds = readNotificationService.getGroupMessageReadUsers(msgId);
            
            // 6. 分页处理
            int total = readUserIds.size();
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, total);
            
            List<String> pagedUserIds = readUserIds.subList(startIndex, endIndex);

            // 7. 构建响应数据
            Map<String, Object> result = new HashMap<>();
            result.put("msgId", msgId);
            result.put("readCount", total);
            result.put("readUsers", pagedUserIds);
            result.put("pagination", Map.of(
                    "page", page,
                    "size", size,
                    "total", total,
                    "totalPages", (total + size - 1) / size
            ));

            log.info("查询消息已读列表成功 - 消息ID: {}, 总已读数: {}, 当前页: {}", msgId, total, page);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("查询消息已读列表失败 - 消息ID: {}", msgId, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取群聊消息的已读数
     * 
     * @param msgId 消息ID
     * @return 已读数
     */
    @GetMapping("/{msgId}/read-count")
    public ApiResponse<Map<String, Object>> getMessageReadCount(@PathVariable String msgId) {
        log.info("查询消息已读数 - 消息ID: {}", msgId);

        try {
            // 1. 参数验证
            if (msgId == null || msgId.trim().isEmpty()) {
                return ApiResponse.error("消息ID不能为空");
            }

            // 2. 获取消息信息，验证消息是否存在
            Map<String, String> msgSenderMap = messageService.getMessageSenders(List.of(msgId));
            if (msgSenderMap.isEmpty()) {
                return ApiResponse.error("消息不存在");
            }

            // 3. 验证是否为群聊消息
            String conversationId = messageService.getMessageConversationId(msgId);
            if (conversationId == null || !conversationId.startsWith("group_")) {
                return ApiResponse.error("只支持查询群聊消息的已读数");
            }

            // 4. 获取已读数
            int readCount = readNotificationService.getGroupMessageReadCount(msgId);

            // 5. 构建响应数据
            Map<String, Object> result = new HashMap<>();
            result.put("msgId", msgId);
            result.put("readCount", readCount);

            log.info("查询消息已读数成功 - 消息ID: {}, 已读数: {}", msgId, readCount);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("查询消息已读数失败 - 消息ID: {}", msgId, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }


}
