package com.vanky.im.message.service.impl;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.service.GroupNotificationService;
import com.vanky.im.message.service.GatewayMessagePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 群聊通知服务实现
 * 用于读扩散模式下的轻量级通知推送
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 实现群聊通知服务，支持读扩散模式的轻量级通知推送;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class GroupNotificationServiceImpl implements GroupNotificationService {
    
    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;
    
    @Override
    public void pushNotificationToUser(ChatMessage notificationMessage, Long conversationSeq, String gatewayNodeId) {
        try {
            // {{CHENGQI:
            // Action: Modified; Timestamp: 2025-08-04 21:00:00 +08:00; Reason: 修正群聊通知推送，确保使用会话级seq而不是原消息seq;
            // }}
            // {{START MODIFICATIONS}}
            log.debug("推送群聊通知给用户 - 用户ID: {}, 会话ID: {}, 消息ID: {}, 会话seq: {}, 网关: {}",
                    notificationMessage.getToId(), notificationMessage.getConversationId(),
                    notificationMessage.getUid(), conversationSeq, gatewayNodeId);

            // 使用会话级seq推送通知
            gatewayMessagePushService.pushNotificationToGateway(notificationMessage, conversationSeq, gatewayNodeId);

            log.debug("群聊通知推送成功 - 用户ID: {}, 会话ID: {}, 会话seq: {}",
                    notificationMessage.getToId(), notificationMessage.getConversationId(), conversationSeq);
            // {{END MODIFICATIONS}}

        } catch (Exception e) {
            log.error("推送群聊通知失败 - 用户ID: {}, 会话ID: {}, 会话seq: {}",
                    notificationMessage.getToId(), notificationMessage.getConversationId(), conversationSeq, e);
        }
    }
    
    @Override
    public void pushNotificationToOnlineMembers(ChatMessage originalMessage, Long conversationSeq,
                                              Map<String, String> onlineMembers) {

        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-04 21:00:00 +08:00; Reason: 修正批量推送逻辑，使用会话级seq;
        // }}
        // {{START MODIFICATIONS}}
        log.info("开始批量推送群聊通知 - 会话ID: {}, 消息ID: {}, 会话seq: {}, 在线成员数: {}",
                originalMessage.getConversationId(), originalMessage.getUid(), conversationSeq, onlineMembers.size());

        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, String> entry : onlineMembers.entrySet()) {
            String memberId = entry.getKey();
            String gatewayNodeId = entry.getValue();

            try {
                // 跳过发送者自己
                if (memberId.equals(originalMessage.getFromId())) {
                    continue;
                }

                // 为每个成员创建专门的通知消息，传递会话级seq
                ChatMessage notificationMessage = createNotificationMessage(originalMessage, memberId, conversationSeq);

                // 推送通知，使用会话级seq
                pushNotificationToUser(notificationMessage, conversationSeq, gatewayNodeId);
                successCount++;

            } catch (Exception e) {
                log.error("推送群聊通知给成员失败 - 成员ID: {}, 会话ID: {}", memberId, originalMessage.getConversationId(), e);
                failureCount++;
            }
        }

        log.info("群聊通知批量推送完成 - 会话ID: {}, 会话seq: {}, 成功: {}, 失败: {}",
                originalMessage.getConversationId(), conversationSeq, successCount, failureCount);
        // {{END MODIFICATIONS}}
    }
    
    @Override
    public ChatMessage createNotificationMessage(ChatMessage originalMessage, String targetUserId, Long conversationSeq) {

        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-04 21:00:00 +08:00; Reason: 修正通知消息创建，使用会话级seq而不是原消息seq;
        // }}
        // {{START MODIFICATIONS}}
        // 创建轻量级通知消息，使用特殊的消息类型
        // 通知消息的关键字段：
        // 1. type: 特殊的通知类型，标识这是群聊通知
        // 2. content: 简化的通知内容
        // 3. fromId: 发送方用户ID
        // 4. toId: 接收方用户ID（目标用户ID，不是群组ID）
        // 5. conversationId: 群聊会话ID，用于标识来源群聊
        // 6. seq: 会话级序列号，用于ACK确认

        // 限制内容长度，创建简化的通知内容
        String notificationContent = "new_message"; // 简化的通知内容
        if (originalMessage.getContent() != null && originalMessage.getContent().length() > 20) {
            notificationContent = originalMessage.getContent().substring(0, 17) + "...";
        } else if (originalMessage.getContent() != null) {
            notificationContent = originalMessage.getContent();
        }

        ChatMessage notificationMessage = ChatMessage.newBuilder()
                .setType(MessageTypeConstants.GROUP_MESSAGE_NOTIFICATION) // 特殊的通知类型
                .setContent(notificationContent) // 简化的通知内容
                .setFromId(originalMessage.getFromId()) // 保持发送方ID
                .setToId(targetUserId) // 关键修正：设置为目标用户ID，不是群组ID
                .setUid(originalMessage.getUid()) // 使用原消息ID
                .setSeq(String.valueOf(conversationSeq)) // 关键修正：使用会话级seq，不是原消息seq
                .setTimestamp(originalMessage.getTimestamp()) // 使用原消息时间戳
                .setRetry(0) // 通知不重试
                .setConversationId(originalMessage.getConversationId()) // 设置会话ID，标识来源群聊
                // 推拉结合模式新增字段
                .setUserSeq(0L) // 群聊消息不使用用户级全局序列号
                .setConversationSeq(conversationSeq) // 设置会话级序列号
                .setExpectedSeq(0L) // 通知消息不需要期望序列号
                .build();

        log.debug("创建群聊通知消息 - 会话ID: {}, 目标用户: {}, 会话seq: {}, 通知内容: {}",
                originalMessage.getConversationId(), targetUserId, conversationSeq, notificationContent);

        return notificationMessage;
        // {{END MODIFICATIONS}}
    }
}
// {{END MODIFICATIONS}}
