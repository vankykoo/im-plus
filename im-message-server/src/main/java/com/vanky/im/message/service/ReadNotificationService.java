package com.vanky.im.message.service;

import java.util.List;

/**
 * 已读通知服务接口
 * 负责构建和发送消息已读状态变化的通知
 * 
 * @author vanky
 * @since 2025-08-12
 */
public interface ReadNotificationService {

    /**
     * 发送私聊已读通知
     * 通知消息发送方，接收方已读到指定序列号
     * 
     * @param conversationId 会话ID
     * @param readerId 已读用户ID
     * @param lastReadSeq 已读到的最大序列号
     */
    void sendPrivateReadNotification(String conversationId, String readerId, long lastReadSeq);

    /**
     * 发送群聊已读通知
     * 通知消息发送方，有用户已读了指定的消息
     * 
     * @param conversationId 会话ID
     * @param messageIds 已读的消息ID列表
     * @param readerId 已读用户ID
     */
    void sendGroupReadNotifications(String conversationId, List<String> messageIds, String readerId);

    /**
     * 获取群聊消息的已读数
     * 
     * @param msgId 消息ID
     * @return 已读数
     */
    int getGroupMessageReadCount(String msgId);

    /**
     * 获取群聊消息的已读用户列表（仅小群支持）
     * 
     * @param msgId 消息ID
     * @return 已读用户ID列表
     */
    List<String> getGroupMessageReadUsers(String msgId);
}
