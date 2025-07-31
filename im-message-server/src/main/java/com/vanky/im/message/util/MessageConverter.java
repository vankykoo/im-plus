package com.vanky.im.message.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constants.MessageTypeConstants;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.entity.PrivateMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.UUID;

/**
 * 消息转换工具类
 */
@Slf4j
public class MessageConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将ChatMessage转换为PrivateMessage
     * @param chatMessage 原始消息
     * @param msgId 全局消息ID
     * @param conversationId 会话ID
     * @return PrivateMessage实体
     * @deprecated 请使用 convertToMessage(ChatMessage, String, String, Integer) 方法
     */
    @Deprecated
    public static PrivateMessage convertToPrivateMessage(ChatMessage chatMessage, String msgId, String conversationId) {
        PrivateMessage privateMessage = new PrivateMessage();
        
        privateMessage.setMsgId(Long.valueOf(msgId)); // 雪花算法生成的ID直接转换为Long
        privateMessage.setConversationId(conversationId);
        privateMessage.setUserId(Long.valueOf(chatMessage.getFromId())); // 将String转换为Long
        privateMessage.setContent(chatMessage.getContent());
        privateMessage.setStatus(1); // 1-正常
        privateMessage.setSendTime(new Date(chatMessage.getTimestamp()));
        
        return privateMessage;
    }

    /**
     * 将PrivateMessage转换为JSON字符串
     * @param privateMessage 私聊消息实体
     * @return JSON字符串
     */
    public static String toJson(PrivateMessage privateMessage) {
        try {
            return objectMapper.writeValueAsString(privateMessage);
        } catch (JsonProcessingException e) {
            log.error("消息转换为JSON失败", e);
            throw new RuntimeException("消息转换为JSON失败", e);
        }
    }

    /**
     * 从JSON字符串转换为PrivateMessage
     * @param json JSON字符串
     * @return PrivateMessage实体
     */
    public static PrivateMessage fromJson(String json) {
        try {
            return objectMapper.readValue(json, PrivateMessage.class);
        } catch (JsonProcessingException e) {
            log.error("JSON转换为消息失败", e);
            throw new RuntimeException("JSON转换为消息失败", e);
        }
    }

    /**
     * 生成会话ID
     * @param fromUserId 发送方用户ID
     * @param toUserId 接收方用户ID
     * @return 会话ID
     */
    public static String generateConversationId(String fromUserId, String toUserId) {
        // 私聊会话ID规则：private_xx_xx，其中userid小的放前面
        try {
            long id1 = Long.parseLong(fromUserId);
            long id2 = Long.parseLong(toUserId);

            // 使用较小的ID在前，较大的ID在后，确保同一对用户的会话ID始终相同
            if (id1 < id2) {
                return "private_" + id1 + "_" + id2;
            } else {
                return "private_" + id2 + "_" + id1;
            }
        } catch (NumberFormatException e) {
            // 如果ID不是数字，则使用字符串比较
            if (fromUserId.compareTo(toUserId) < 0) {
                return "private_" + fromUserId + "_" + toUserId;
            } else {
                return "private_" + toUserId + "_" + fromUserId;
            }
        }
    }

    /**
     * 将ChatMessage转换为GroupMessage
     * @param chatMessage 原始消息
     * @param msgId 全局消息ID
     * @param conversationId 会话ID
     * @return GroupMessage实体
     * @deprecated 请使用 convertToMessage(ChatMessage, String, String, Integer) 方法
     */
    @Deprecated
    public static GroupMessage convertToGroupMessage(ChatMessage chatMessage, String msgId, String conversationId) {
        GroupMessage groupMessage = new GroupMessage();
        
        groupMessage.setMsgId(Long.valueOf(msgId)); // 雪花算法生成的ID直接转换为Long
        groupMessage.setConversationId(conversationId);
        groupMessage.setUserId(Long.valueOf(chatMessage.getFromId())); // 将String转换为Long
        groupMessage.setContent(chatMessage.getContent());
        groupMessage.setStatus(1); // 1-正常
        groupMessage.setSendTime(new Date(chatMessage.getTimestamp()));
        
        return groupMessage;
    }
    
    /**
     * 将GroupMessage转换为JSON字符串
     * @param groupMessage 群聊消息实体
     * @return JSON字符串
     */
    public static String toJson(GroupMessage groupMessage) {
        try {
            return objectMapper.writeValueAsString(groupMessage);
        } catch (JsonProcessingException e) {
            log.error("群聊消息转换为JSON失败", e);
            throw new RuntimeException("群聊消息转换为JSON失败", e);
        }
    }
    
    /**
     * 从JSON字符串转换为GroupMessage
     * @param json JSON字符串
     * @return GroupMessage实体
     */
    public static GroupMessage groupMessageFromJson(String json) {
        try {
            return objectMapper.readValue(json, GroupMessage.class);
        } catch (JsonProcessingException e) {
            log.error("JSON转换为群聊消息失败", e);
            throw new RuntimeException("JSON转换为群聊消息失败", e);
        }
    }
    
    /**
     * 生成群聊会话ID
     * @param groupId 群组ID
     * @return 群聊会话ID
     */
    public static String generateGroupConversationId(String groupId) {
        return "group_" + groupId;
    }

    /**
     * 生成全局唯一消息ID
     * @return 消息ID
     */
    public static String generateMsgId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 统一消息转换方法 ====================

    /**
     * 将ChatMessage转换为统一的Message实体
     * @param chatMessage 原始消息
     * @param msgId 全局消息ID
     * @param conversationId 会话ID
     * @param msgType 消息类型（1-私聊，2-群聊）
     * @return Message实体
     */
    public static Message convertToMessage(ChatMessage chatMessage, String msgId, String conversationId, Byte msgType) {
        Message message = new Message();

        message.setMsgId(Long.valueOf(msgId)); // 雪花算法生成的ID直接转换为Long
        message.setConversationId(conversationId);
        message.setSenderId(Long.valueOf(chatMessage.getFromId())); // 将String转换为Long
        message.setMsgType(msgType);
        message.setContentType(determineContentType(chatMessage)); // 自动判断内容类型
        message.setContent(chatMessage.getContent());
        message.setStatus((byte) 0); // 初始状态为已发送，等待客户端确认
        message.setSendTime(new Date(chatMessage.getTimestamp()));

        return message;
    }

    /**
     * 将Message转换为JSON字符串
     * @param message 消息实体
     * @return JSON字符串
     */
    public static String toJson(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("消息转换为JSON失败", e);
            throw new RuntimeException("消息转换为JSON失败", e);
        }
    }

    /**
     * 从JSON字符串转换为Message
     * @param json JSON字符串
     * @return Message实体
     */
    public static Message messageFromJson(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            log.error("JSON转换为消息失败", e);
            throw new RuntimeException("JSON转换为消息失败", e);
        }
    }

    /**
     * 根据ChatMessage内容自动判断内容类型
     * @param chatMessage 原始消息
     * @return 内容类型
     */
    private static Byte determineContentType(ChatMessage chatMessage) {
        // 目前默认为文本类型，后续可以根据消息内容或其他字段进行判断
        // 例如：根据content的格式、文件扩展名等
        return MessageTypeConstants.CONTENT_TYPE_TEXT;
    }
}