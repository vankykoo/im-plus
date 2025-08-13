package com.vanky.im.message.processor;

import com.vanky.im.common.protocol.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息发送回执处理器
 * 
 * 处理MESSAGE_SEND_RECEIPT类型的消息
 * 注意：此处理器主要用于服务端的消息类型注册和路由
 * 实际的回执处理逻辑在客户端完成
 * 
 * 设计原则：
 * - SRP（单一职责）：专门处理发送回执消息的服务端逻辑
 * - ISP（接口隔离）：实现统一的MessageProcessor接口
 * - LSP（里氏替换）：可以替换其他MessageProcessor实现
 * 
 * @author vanky
 * @since 2025-08-13
 */
@Slf4j
@Component
public class MessageSendReceiptProcessor {

    /**
     * 处理消息发送回执
     * 
     * 在服务端，MESSAGE_SEND_RECEIPT类型的消息主要用于：
     * 1. 消息类型的注册和识别
     * 2. 统计和监控
     * 3. 日志记录
     * 
     * 实际的回执处理逻辑（如更新本地消息状态、移除待确认队列等）
     * 在客户端的PendingMessageManager中完成
     * 
     * @param chatMessage 回执消息
     */
    public void processMessageSendReceipt(ChatMessage chatMessage) {
        String clientSeq = chatMessage.getClientSeq();
        String serverMsgId = chatMessage.getUid();
        String senderId = chatMessage.getFromId();
        Long userSeq = chatMessage.getUserSeq();
        
        log.info("服务端收到消息发送回执 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}, 用户序列号: {}", 
                senderId, clientSeq, serverMsgId, userSeq);
        
        try {
            // 验证回执消息的有效性
            if (!validateReceiptMessage(chatMessage)) {
                log.warn("回执消息验证失败 - 发送方: {}, 客户端序列号: {}", senderId, clientSeq);
                return;
            }
            
            // 服务端对回执消息的处理主要是统计和日志
            // 实际的业务逻辑（如更新消息状态）在客户端完成
            
            log.debug("消息发送回执处理完成 - 发送方: {}, 客户端序列号: {}", senderId, clientSeq);
            
        } catch (Exception e) {
            log.error("处理消息发送回执异常 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}", 
                     senderId, clientSeq, serverMsgId, e);
        }
    }

    /**
     * 验证回执消息的有效性
     * 
     * @param chatMessage 回执消息
     * @return true-有效，false-无效
     */
    private boolean validateReceiptMessage(ChatMessage chatMessage) {
        // 检查必要字段
        if (chatMessage.getFromId() == null || chatMessage.getFromId().trim().isEmpty()) {
            log.warn("回执消息发送方ID为空");
            return false;
        }
        
        if (chatMessage.getClientSeq() == null || chatMessage.getClientSeq().trim().isEmpty()) {
            log.warn("回执消息客户端序列号为空");
            return false;
        }
        
        if (chatMessage.getUid() == null || chatMessage.getUid().trim().isEmpty()) {
            log.warn("回执消息服务端消息ID为空");
            return false;
        }
        
        if (chatMessage.getUserSeq() <= 0) {
            log.warn("回执消息用户序列号无效: {}", chatMessage.getUserSeq());
            return false;
        }
        
        return true;
    }
}
