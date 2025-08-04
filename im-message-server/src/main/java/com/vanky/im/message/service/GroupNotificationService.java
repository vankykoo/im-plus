package com.vanky.im.message.service;

import com.vanky.im.common.protocol.ChatMessage;

/**
 * 群聊通知服务接口
 * 用于读扩散模式下的轻量级通知推送
 *
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Modified; Timestamp: 2025-08-02 22:27:58 +08:00; Reason: 修正群聊通知服务，直接使用ChatMessage协议而不是自定义通知对象;
// }}
// {{START MODIFICATIONS}}
public interface GroupNotificationService {

    /**
     * 推送群聊消息通知给指定用户
     *
     * @param notificationMessage 通知消息（ChatMessage格式，toId为目标用户ID）
     * @param conversationSeq 会话级序列号
     * @param gatewayNodeId 用户所在的网关节点ID
     */
    void pushNotificationToUser(ChatMessage notificationMessage, Long conversationSeq, String gatewayNodeId);

    /**
     * 批量推送群聊消息通知给多个在线用户
     *
     * @param originalMessage 原始群聊消息
     * @param conversationSeq 会话级序列号
     * @param onlineMembers 在线成员列表（用户ID -> 网关节点ID）
     */
    void pushNotificationToOnlineMembers(ChatMessage originalMessage, Long conversationSeq,
                                       java.util.Map<String, String> onlineMembers);

    /**
     * 创建群聊消息通知（ChatMessage格式）
     * 通知消息的特点：
     * - type: 特殊的通知类型
     * - content: 简化的通知内容
     * - toId: 目标用户ID（不是群组ID）
     * - conversationId: 会话ID
     * - seq: 会话级序列号
     *
     * @param originalMessage 原始群聊消息
     * @param targetUserId 目标用户ID
     * @param conversationSeq 会话级序列号
     * @return 通知消息（ChatMessage格式）
     */
    ChatMessage createNotificationMessage(ChatMessage originalMessage, String targetUserId, Long conversationSeq);
}
// {{END MODIFICATIONS}}
