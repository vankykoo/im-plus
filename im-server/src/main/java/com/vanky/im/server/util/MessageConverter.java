package com.vanky.im.server.util;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.server.entity.PrivateMessage;
import com.vanky.im.server.entity.GroupMessage;

import java.util.Date;

/**
 * @author vanky
 * @create 2025/1/15
 * @description 消息转换工具类
 */
public class MessageConverter {

    /**
     * 将ChatMessage转换为PrivateMessage实体
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID
     * @return PrivateMessage实体
     */
    public static PrivateMessage toPrivateMessage(ChatMessage chatMessage, String conversationId) {
        PrivateMessage privateMessage = new PrivateMessage();
        privateMessage.setMsgId(Long.parseLong(chatMessage.getUid()));
        privateMessage.setConversationId(conversationId);
        privateMessage.setUserId(Long.parseLong(chatMessage.getFromId()));
        privateMessage.setContent(chatMessage.getContent());
        privateMessage.setStatus(1); // 1表示正常状态
        privateMessage.setSendTime(new Date(chatMessage.getTimestamp()));
        return privateMessage;
    }
    
    /**
     * 将ChatMessage转换为GroupMessage实体
     * @param chatMessage 聊天消息
     * @param conversationId 会话ID
     * @return GroupMessage实体
     */
    public static GroupMessage toGroupMessage(ChatMessage chatMessage, String conversationId) {
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setMsgId(Long.parseLong(chatMessage.getUid()));
        groupMessage.setConversationId(conversationId);
        groupMessage.setUserId(Long.parseLong(chatMessage.getFromId()));
        groupMessage.setContent(chatMessage.getContent());
        groupMessage.setStatus(1); // 1表示正常状态
        groupMessage.setSendTime(new Date(chatMessage.getTimestamp()));
        return groupMessage;
    }
}