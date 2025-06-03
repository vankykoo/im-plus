package com.vanky.im.gateway.controller;

import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.gateway.mq.MessageQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vanky
 * @create 2025/6/4 21:50
 * @description 消息队列测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/mq")
public class MQTestController {

    private final MessageQueueService messageQueueService;

    @Autowired
    public MQTestController(MessageQueueService messageQueueService) {
        this.messageQueueService = messageQueueService;
    }

    /**
     * 发送测试消息到消息队列
     * @param fromId 发送方ID
     * @param toId 接收方ID
     * @param content 消息内容
     * @return 结果
     */
    @GetMapping("/send")
    public Map<String, Object> sendTestMessage(
            @RequestParam("fromId") String fromId,
            @RequestParam("toId") String toId,
            @RequestParam("content") String content) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 生成会话ID
            String conversationId = generateConversationId(fromId, toId);
            
            // 构建测试消息
            ChatMessage testMessage = ChatMessage.newBuilder()
                    .setType(ClientToClientMessageType.P2P_CHAT_MESSAGE.getValue())
                    .setContent(content)
                    .setFromId(fromId)
                    .setToId(toId)
                    .setUid("test-" + System.currentTimeMillis())
                    .setSeq(String.valueOf(System.currentTimeMillis()))
                    .setTimestamp(System.currentTimeMillis())
                    .setRetry(0)
                    .build();
            
            // 发送消息到队列
            messageQueueService.sendMessage(conversationId, testMessage);
            
            result.put("success", true);
            result.put("message", "测试消息已发送到队列");
            result.put("conversationId", conversationId);
            result.put("messageId", testMessage.getUid());
            
            log.info("测试消息已发送 - 会话ID: {}, 消息ID: {}", conversationId, testMessage.getUid());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送测试消息失败: " + e.getMessage());
            log.error("发送测试消息失败: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 生成会话ID
     * 对于私聊，使用两个用户ID的组合生成唯一的会话ID
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return 会话ID
     */
    private String generateConversationId(String userId1, String userId2) {
        // 确保会话ID的唯一性，无论用户ID的顺序如何
        long id1 = Long.parseLong(userId1);
        long id2 = Long.parseLong(userId2);
        
        // 使用较小的ID在前，较大的ID在后，确保同一对用户的会话ID始终相同
        if (id1 < id2) {
            return "private_" + id1 + "_" + id2;
        } else {
            return "private_" + id2 + "_" + id1;
        }
    }
}