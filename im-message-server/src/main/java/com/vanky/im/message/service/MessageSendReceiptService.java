package com.vanky.im.message.service;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息发送回执服务
 * 
 * 核心功能：
 * 1. 按照技术方案3.2节规范构建MESSAGE_SEND_RECEIPT类型的回执消息
 * 2. 向发送方推送确认回执，实现端到端的消息发送确认机制
 * 3. 确保回执包含服务端权威的uid、userSeq、timestamp等字段
 * 
 * 设计原则：
 * - SRP（单一职责）：专门负责回执相关逻辑
 * - DIP（依赖倒置）：依赖抽象的推送服务接口
 * - KISS（简单至上）：复用现有的推送机制，不引入复杂架构
 * 
 * @author vanky
 * @since 2025-08-13
 */
@Slf4j
@Service
public class MessageSendReceiptService {

    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;
    
    @Autowired
    private OnlineStatusService onlineStatusService;

    /**
     * 向发送方发送消息发送确认回执
     * 
     * 按照技术方案3.2节规范填充回执字段：
     * - type: MESSAGE_SEND_RECEIPT
     * - clientSeq: 从原始请求中原样复制（用于客户端匹配）
     * - uid: 服务端生成的全局唯一消息ID
     * - userSeq: 服务端为发送方生成的userSeq
     * - timestamp: 服务端权威时间戳
     * - fromId, toId, content, conversationId: 从原始请求中原样复制
     * 
     * @param originalMessage 原始消息请求
     * @param serverMsgId 服务端生成的消息ID
     * @param senderUserSeq 发送方的userSeq
     * @param serverTimestamp 服务端时间戳
     */
    public void sendReceiptToSender(ChatMessage originalMessage, String serverMsgId, 
                                  Long senderUserSeq, long serverTimestamp) {
        
        String senderId = originalMessage.getFromId();
        String clientSeq = originalMessage.getClientSeq();
        
        log.info("准备发送消息发送回执 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}, 用户序列号: {}", 
                senderId, clientSeq, serverMsgId, senderUserSeq);
        
        try {
            // 检查发送方是否在线
            UserSession senderSession = onlineStatusService.getUserOnlineStatus(senderId);
            if (senderSession == null || senderSession.getNodeId() == null) {
                log.info("发送方离线，跳过回执发送 - 发送方: {}, 客户端序列号: {}", senderId, clientSeq);
                return;
            }
            
            // 构建回执消息
            ChatMessage receiptMessage = buildReceiptMessage(originalMessage, serverMsgId, 
                                                            senderUserSeq, serverTimestamp);
            
            // 推送回执到发送方所在的网关
            gatewayMessagePushService.pushMessageToGateway(receiptMessage, senderUserSeq, senderId);
            
            log.info("消息发送回执推送成功 - 发送方: {}, 客户端序列号: {}, 网关: {}", 
                    senderId, clientSeq, senderSession.getNodeId());
            
        } catch (Exception e) {
            // 回执发送失败不应影响主消息处理流程，只记录错误日志
            log.error("消息发送回执推送失败 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}", 
                     senderId, clientSeq, serverMsgId, e);
        }
    }

    /**
     * 构建消息发送回执
     * 
     * 严格按照技术方案3.2节的字段填充规范构建回执消息
     * 
     * @param originalMessage 原始消息请求
     * @param serverMsgId 服务端生成的消息ID
     * @param senderUserSeq 发送方的userSeq
     * @param serverTimestamp 服务端时间戳
     * @return 构建好的回执消息
     */
    private ChatMessage buildReceiptMessage(ChatMessage originalMessage, String serverMsgId,
                                          Long senderUserSeq, long serverTimestamp) {
        
        return ChatMessage.newBuilder()
                // 【行为驱动】客户端识别该消息为"发送回执"的核心标识
                .setType(MessageTypeConstants.MESSAGE_SEND_RECEIPT)
                
                // 【匹配与去重】客户端匹配本地"待确认消息"的唯一关键
                .setClientSeq(originalMessage.getClientSeq())
                
                // 【官方ID】客户端需用此ID更新本地记录，用于后续操作（如撤回）
                .setUid(serverMsgId)
                
                // 【多端同步基石】客户端收到后必须更新本地的lastSyncUserSeq
                .setUserSeq(senderUserSeq)
                
                // 【精准排序】避免因客户端时间不准导致的消息排序问题
                .setTimestamp(serverTimestamp)
                
                // 【上下文】让客户端无需再次查询本地数据库就能获得完整的消息上下文
                .setFromId(originalMessage.getFromId())
                .setToId(originalMessage.getToId())
                .setContent(originalMessage.getContent())
                .setConversationId(originalMessage.getConversationId())
                
                .build();
    }

    /**
     * 向群聊发送方发送消息发送确认回执
     * 
     * 群聊场景下，使用会话级序列号作为userSeq
     * 
     * @param originalMessage 原始群聊消息请求
     * @param serverMsgId 服务端生成的消息ID
     * @param conversationSeq 会话级序列号
     * @param serverTimestamp 服务端时间戳
     */
    public void sendGroupReceiptToSender(ChatMessage originalMessage, String serverMsgId,
                                       Long conversationSeq, long serverTimestamp) {
        
        String senderId = originalMessage.getFromId();
        String clientSeq = originalMessage.getClientSeq();
        
        log.info("准备发送群聊消息发送回执 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}, 会话序列号: {}", 
                senderId, clientSeq, serverMsgId, conversationSeq);
        
        try {
            // 检查发送方是否在线
            UserSession senderSession = onlineStatusService.getUserOnlineStatus(senderId);
            if (senderSession == null || senderSession.getNodeId() == null) {
                log.info("群聊发送方离线，跳过回执发送 - 发送方: {}, 客户端序列号: {}", senderId, clientSeq);
                return;
            }
            
            // 构建群聊回执消息（使用会话级序列号）
            ChatMessage receiptMessage = buildGroupReceiptMessage(originalMessage, serverMsgId,
                                                                conversationSeq, serverTimestamp);
            
            // 推送回执到发送方所在的网关
            gatewayMessagePushService.pushMessageToGateway(receiptMessage, conversationSeq, senderId);
            
            log.info("群聊消息发送回执推送成功 - 发送方: {}, 客户端序列号: {}, 网关: {}", 
                    senderId, clientSeq, senderSession.getNodeId());
            
        } catch (Exception e) {
            // 回执发送失败不应影响主消息处理流程，只记录错误日志
            log.error("群聊消息发送回执推送失败 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}", 
                     senderId, clientSeq, serverMsgId, e);
        }
    }
    /**
     * 构建群聊消息发送回执
     * <p>
     * 专门为群聊场景创建，确保同时设置userSeq和conversationSeq
     *
     * @param originalMessage 原始消息请求
     * @param serverMsgId     服务端生成的消息ID
     * @param conversationSeq 会话级序列号
     * @param serverTimestamp 服务端时间戳
     * @return 构建好的回执消息
     */
    private ChatMessage buildGroupReceiptMessage(ChatMessage originalMessage, String serverMsgId,
                                                 Long conversationSeq, long serverTimestamp) {

        return ChatMessage.newBuilder()
                // 核心标识
                .setType(MessageTypeConstants.MESSAGE_SEND_RECEIPT)
                // 客户端匹配关键
                .setClientSeq(originalMessage.getClientSeq())
                // 服务端官方ID
                .setUid(serverMsgId)
                // 群聊场景，userSeq可以不设置或设置为0，关键是conversationSeq
                .setUserSeq(originalMessage.getUserSeq()) // 保留原始的userSeq或设置为0
                // 【关键修复】设置会话级序列号
                .setConversationSeq(conversationSeq)
                // 服务端权威时间戳
                .setTimestamp(serverTimestamp)
                // 附带上下文信息
                .setFromId(originalMessage.getFromId())
                .setToId(originalMessage.getToId())
                .setContent(originalMessage.getContent())
                .setConversationId(originalMessage.getConversationId())
                .build();
    }
}
