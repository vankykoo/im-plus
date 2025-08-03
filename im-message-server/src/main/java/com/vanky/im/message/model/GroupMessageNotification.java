package com.vanky.im.message.model;

import lombok.Data;

/**
 * 群聊消息轻量级通知
 * 用于读扩散模式下的群聊消息推送
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 创建群聊消息轻量级通知数据结构，支持读扩散模式的通知推送;
// }}
// {{START MODIFICATIONS}}
@Data
public class GroupMessageNotification {
    
    /**
     * 通知类型
     */
    private String type = "new_group_message_notify";
    
    /**
     * 会话ID（群聊ID）
     */
    private String conversationId;
    
    /**
     * 会话的最新序列号
     */
    private Long newSeq;
    
    /**
     * 发送方用户ID（可选，用于客户端显示）
     */
    private String fromUserId;
    
    /**
     * 消息预览内容（可选，用于通知显示）
     */
    private String preview;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 目标用户ID（接收通知的用户）
     */
    private String targetUserId;
    
    public GroupMessageNotification() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public GroupMessageNotification(String conversationId, Long newSeq, String fromUserId, String targetUserId) {
        this();
        this.conversationId = conversationId;
        this.newSeq = newSeq;
        this.fromUserId = fromUserId;
        this.targetUserId = targetUserId;
    }
    
    public GroupMessageNotification(String conversationId, Long newSeq, String fromUserId, String preview, String targetUserId) {
        this(conversationId, newSeq, fromUserId, targetUserId);
        this.preview = preview;
    }
    
    /**
     * 转换为JSON字符串（简化实现）
     */
    public String toJson() {
        return String.format(
            "{\"type\":\"%s\",\"conversation_id\":\"%s\",\"new_seq\":%d,\"from_user_id\":\"%s\",\"preview\":\"%s\",\"timestamp\":%d,\"target_user_id\":\"%s\"}",
            type, conversationId, newSeq, 
            fromUserId != null ? fromUserId : "",
            preview != null ? preview.replace("\"", "\\\"") : "",
            timestamp,
            targetUserId != null ? targetUserId : ""
        );
    }
    
    @Override
    public String toString() {
        return "GroupMessageNotification{" +
                "type='" + type + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", newSeq=" + newSeq +
                ", fromUserId='" + fromUserId + '\'' +
                ", preview='" + preview + '\'' +
                ", timestamp=" + timestamp +
                ", targetUserId='" + targetUserId + '\'' +
                '}';
    }
}
// {{END MODIFICATIONS}}
