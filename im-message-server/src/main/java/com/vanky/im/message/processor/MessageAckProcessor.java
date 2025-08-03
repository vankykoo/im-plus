package com.vanky.im.message.processor;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.service.MessageStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息确认处理器
 * 处理客户端发送的ACK确认消息，更新消息推送状态
 */
@Slf4j
@Component
public class MessageAckProcessor {

    @Autowired
    private MessageStatusService messageStatusService;

    /**
     * 处理消息确认
     * @param chatMessage ACK确认消息
     */
    public void processMessageAck(ChatMessage chatMessage) {
        String msgId = chatMessage.getUid();
        String seq = chatMessage.getSeq();
        String userId = chatMessage.getFromId();
        
        log.info("开始处理消息确认 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
        
        try {
            // 验证ACK消息的有效性
            if (!validateAckMessage(chatMessage)) {
                log.warn("ACK消息验证失败 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
                return;
            }
            
            // 更新消息状态为已送达
            boolean updated = messageStatusService.updateMessageDelivered(msgId, seq, userId);
            
            if (updated) {
                log.info("消息确认处理成功 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
            } else {
                log.warn("消息确认处理失败，未找到对应消息 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
            }
            
        } catch (Exception e) {
            log.error("处理消息确认异常 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId, e);
        }
    }

    /**
     * 处理批量消息确认
     * @param chatMessage 批量ACK确认消息，消息内容包含多个消息ID，用逗号分隔
     */
    public void processBatchMessageAck(ChatMessage chatMessage) {
        String userId = chatMessage.getFromId();
        String msgIdsStr = chatMessage.getContent(); // 消息内容包含多个消息ID，用逗号分隔

        log.info("开始处理批量消息确认 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr);

        try {
            // 验证批量ACK消息的有效性
            if (!validateBatchAckMessage(chatMessage)) {
                log.warn("批量ACK消息验证失败 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr);
                return;
            }

            // 解析消息ID列表
            String[] msgIds = msgIdsStr.split(",");
            if (msgIds.length == 0) {
                log.warn("批量ACK消息ID列表为空 - 用户: {}", userId);
                return;
            }

            // 批量更新消息状态为已送达
            int updatedCount = messageStatusService.batchUpdateMessageDelivered(msgIds, userId);

            log.info("批量消息确认处理完成 - 用户: {}, 总消息数: {}, 成功更新: {}",
                    userId, msgIds.length, updatedCount);

        } catch (Exception e) {
            log.error("处理批量消息确认异常 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr, e);
        }
    }

    /**
     * 验证ACK消息的有效性
     * @param chatMessage ACK消息
     * @return 验证结果
     */
    private boolean validateAckMessage(ChatMessage chatMessage) {
        // 检查必要字段
        if (chatMessage.getUid() == null || chatMessage.getUid().isEmpty()) {
            log.warn("ACK消息缺少消息ID");
            return false;
        }
        
        if (chatMessage.getSeq() == null || chatMessage.getSeq().isEmpty()) {
            log.warn("ACK消息缺少序列号");
            return false;
        }
        
        if (chatMessage.getFromId() == null || chatMessage.getFromId().isEmpty()) {
            log.warn("ACK消息缺少用户ID");
            return false;
        }
        
        // 检查消息类型
        if (chatMessage.getType() != MessageTypeConstants.MESSAGE_ACK) {
            log.warn("ACK消息类型不正确 - 期望: {}, 实际: {}",
                    MessageTypeConstants.MESSAGE_ACK,
                    chatMessage.getType());
            return false;
        }
        
        return true;
    }

    /**
     * 验证批量ACK消息的有效性
     * @param chatMessage 批量ACK消息
     * @return 是否有效
     */
    private boolean validateBatchAckMessage(ChatMessage chatMessage) {
        // 检查用户ID
        if (chatMessage.getFromId() == null || chatMessage.getFromId().trim().isEmpty()) {
            log.warn("批量ACK消息缺少用户ID");
            return false;
        }

        // 检查消息内容（消息ID列表）
        if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
            log.warn("批量ACK消息缺少消息ID列表");
            return false;
        }

        // 检查消息类型
        if (chatMessage.getType() != MessageTypeConstants.BATCH_MESSAGE_ACK) {
            log.warn("批量ACK消息类型不正确: {}", chatMessage.getType());
            return false;
        }

        return true;
    }
}
