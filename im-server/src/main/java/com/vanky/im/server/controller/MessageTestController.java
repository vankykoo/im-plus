package com.vanky.im.server.controller;

import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.service.PrivateMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author vanky
 * @create 2025/6/5
 * @description 消息测试控制器
 */
@RestController
@RequestMapping("/api/message")
public class MessageTestController {

    @Autowired
    private PrivateMessageService privateMessageService;

    /**
     * 查询私聊消息列表
     * @param conversationId 会话ID（可选）
     * @param limit 限制数量，默认10条
     * @return 私聊消息列表
     */
    @GetMapping("/private/list")
    public List<PrivateMessage> getPrivateMessages(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "10") int limit) {
        
        if (conversationId != null && !conversationId.isEmpty()) {
            // 查询指定会话的消息
            return privateMessageService.lambdaQuery()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .orderByDesc(PrivateMessage::getSendTime)
                    .last("LIMIT " + limit)
                    .list();
        } else {
            // 查询所有消息
            return privateMessageService.lambdaQuery()
                    .orderByDesc(PrivateMessage::getSendTime)
                    .last("LIMIT " + limit)
                    .list();
        }
    }

    /**
     * 获取消息总数
     * @return 消息总数
     */
    @GetMapping("/private/count")
    public long getPrivateMessageCount() {
        return privateMessageService.count();
    }
}