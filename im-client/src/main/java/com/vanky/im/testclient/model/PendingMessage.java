package com.vanky.im.testclient.model;

/**
 * 待确认消息模型
 * 用于跟踪发送中的消息状态
 * 
 * @author vanky
 * @create 2025-08-05
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-05 11:49:15 +08:00; Reason: 创建待确认消息数据模型，支持消息发送确认机制;
// }}
// {{START MODIFICATIONS}}
public class PendingMessage {
    
    /**
     * 客户端生成的临时序列号，用于回执匹配
     */
    private String clientSeq;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 接收方用户ID
     */
    private String toUserId;
    
    /**
     * 消息发送时间
     */
    private long sendTime;
    
    /**
     * 重试次数
     */
    private int retryCount;
    
    /**
     * 消息状态
     */
    private MessageStatus status;
    
    /**
     * 服务端生成的正式消息ID（回执后填充）
     */
    private String serverMsgId;
    
    /**
     * 会话级序列号（磐石计划：替代serverSeq）
     */
    private String conversationSeq;
    
    /**
     * 消息类型（私聊/群聊）
     */
    private int messageType;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 构造函数
     */
    public PendingMessage() {
        this.sendTime = System.currentTimeMillis();
        this.retryCount = 0;
        this.status = MessageStatus.SENDING;
    }
    
    /**
     * 构造函数
     * @param clientSeq 客户端序列号
     * @param content 消息内容
     * @param toUserId 接收方ID
     * @param messageType 消息类型
     * @param conversationId 会话ID
     */
    public PendingMessage(String clientSeq, String content, String toUserId, 
                         int messageType, String conversationId) {
        this();
        this.clientSeq = clientSeq;
        this.content = content;
        this.toUserId = toUserId;
        this.messageType = messageType;
        this.conversationId = conversationId;
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * 检查是否超时
     * @param timeoutMs 超时时间（毫秒）
     * @return true-超时，false-未超时
     */
    public boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - sendTime > timeoutMs;
    }
    
    /**
     * 更新发送时间（用于重试）
     */
    public void updateSendTime() {
        this.sendTime = System.currentTimeMillis();
    }
    
    // Getter和Setter方法
    public String getClientSeq() {
        return clientSeq;
    }
    
    public void setClientSeq(String clientSeq) {
        this.clientSeq = clientSeq;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getToUserId() {
        return toUserId;
    }
    
    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }
    
    public long getSendTime() {
        return sendTime;
    }
    
    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public String getServerMsgId() {
        return serverMsgId;
    }
    
    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }
    
    public String getConversationSeq() {
        return conversationSeq;
    }

    public void setConversationSeq(String conversationSeq) {
        this.conversationSeq = conversationSeq;
    }
    
    public int getMessageType() {
        return messageType;
    }
    
    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    @Override
    public String toString() {
        return String.format(
            "PendingMessage{clientSeq='%s', content='%s', toUserId='%s', " +
            "sendTime=%d, retryCount=%d, status=%s, serverMsgId='%s', conversationSeq='%s'}",
            clientSeq, content, toUserId, sendTime, retryCount, status, serverMsgId, conversationSeq
        );
    }
}
// {{END MODIFICATIONS}}
