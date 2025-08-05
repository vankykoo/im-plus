package com.vanky.im.testclient.handler;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.testclient.manager.PendingMessageManager;

/**
 * 消息发送回执处理器
 * 处理服务端发送的消息发送回执
 * 
 * @author vanky
 * @create 2025-08-05
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-05 11:50:30 +08:00; Reason: 创建消息发送回执处理器，处理服务端回执消息;
// }}
// {{START MODIFICATIONS}}
public class MessageSendReceiptHandler {
    
    /** 待确认消息管理器 */
    private final PendingMessageManager pendingMessageManager;
    
    /**
     * 构造函数
     * @param pendingMessageManager 待确认消息管理器
     */
    public MessageSendReceiptHandler(PendingMessageManager pendingMessageManager) {
        this.pendingMessageManager = pendingMessageManager;
    }
    
    /**
     * 处理消息发送回执
     * @param receiptMessage 回执消息
     * @return 是否处理成功
     */
    public boolean handleSendReceipt(ChatMessage receiptMessage) {
        if (receiptMessage == null) {
            System.err.println("回执消息为空");
            return false;
        }
        
        // 验证消息类型
        if (receiptMessage.getType() != MessageTypeConstants.MESSAGE_SEND_RECEIPT) {
            System.err.println("消息类型不匹配，期望: " + MessageTypeConstants.MESSAGE_SEND_RECEIPT + 
                             ", 实际: " + receiptMessage.getType());
            return false;
        }
        
        // 提取回执信息
        String clientSeq = receiptMessage.getClientSeq();
        String serverMsgId = receiptMessage.getServerMsgId();
        String serverSeq = receiptMessage.getServerSeq();
        
        // 验证必要字段
        if (isEmptyOrNull(clientSeq)) {
            System.err.println("回执消息中客户端序列号为空");
            return false;
        }
        
        if (isEmptyOrNull(serverMsgId)) {
            System.err.println("回执消息中服务端消息ID为空");
            return false;
        }
        
        if (isEmptyOrNull(serverSeq)) {
            System.err.println("回执消息中服务端序列号为空");
            return false;
        }
        
        System.out.println("收到消息发送回执 - 客户端序列号: " + clientSeq + 
                         ", 服务端消息ID: " + serverMsgId + 
                         ", 服务端序列号: " + serverSeq);
        
        // 委托给待确认消息管理器处理
        boolean success = pendingMessageManager.handleSendReceipt(clientSeq, serverMsgId, serverSeq);
        
        if (success) {
            System.out.println("消息发送回执处理成功: " + clientSeq);
        } else {
            System.err.println("消息发送回执处理失败: " + clientSeq);
        }
        
        return success;
    }
    
    /**
     * 验证回执消息格式
     * @param receiptMessage 回执消息
     * @return 是否有效
     */
    public boolean isValidReceipt(ChatMessage receiptMessage) {
        if (receiptMessage == null) {
            return false;
        }
        
        // 检查消息类型
        if (receiptMessage.getType() != MessageTypeConstants.MESSAGE_SEND_RECEIPT) {
            return false;
        }
        
        // 检查必要字段
        return !isEmptyOrNull(receiptMessage.getClientSeq()) &&
               !isEmptyOrNull(receiptMessage.getServerMsgId()) &&
               !isEmptyOrNull(receiptMessage.getServerSeq());
    }
    
    /**
     * 获取回执信息摘要
     * @param receiptMessage 回执消息
     * @return 回执信息摘要
     */
    public String getReceiptSummary(ChatMessage receiptMessage) {
        if (receiptMessage == null) {
            return "回执消息为空";
        }
        
        return String.format("回执[类型=%d, 客户端序列号=%s, 服务端消息ID=%s, 服务端序列号=%s]",
                receiptMessage.getType(),
                receiptMessage.getClientSeq(),
                receiptMessage.getServerMsgId(),
                receiptMessage.getServerSeq());
    }
    
    /**
     * 检查字符串是否为空或null
     * @param str 待检查的字符串
     * @return true-为空或null，false-有效
     */
    private boolean isEmptyOrNull(String str) {
        return str == null || str.trim().isEmpty();
    }
}
// {{END MODIFICATIONS}}
