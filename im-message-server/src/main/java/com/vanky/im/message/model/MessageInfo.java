package com.vanky.im.message.model;

import lombok.Data;

import java.util.Date;

/**
 * 消息信息DTO
 * 用于离线消息同步时传输消息基本信息
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建消息信息DTO，用于离线消息同步时传输消息内容;
// }}
// {{START MODIFICATIONS}}
@Data
public class MessageInfo {

    /**
     * 消息ID
     */
    private String msgId;

    /**
     * 用户级全局序列号
     */
    private Long seq;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 发送方用户ID
     */
    private String fromUserId;

    /**
     * 接收方用户ID（私聊时使用）
     */
    private String toUserId;

    /**
     * 群组ID（群聊时使用）
     */
    private String groupId;

    /**
     * 消息类型
     * 1-私聊消息，2-群聊消息
     */
    private Byte msgType;

    /**
     * 内容类型
     * 1-文本，2-图片，3-语音，4-视频，5-文件
     */
    private Byte contentType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息状态
     * 0-已发送，1-推送成功，2-已读，3-撤回，4-推送失败
     */
    private Byte status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    public MessageInfo() {
    }

    public MessageInfo(String msgId, Long seq, String conversationId, String fromUserId, 
                      Byte msgType, Byte contentType, String content, Byte status, Date createTime) {
        this.msgId = msgId;
        this.seq = seq;
        this.conversationId = conversationId;
        this.fromUserId = fromUserId;
        this.msgType = msgType;
        this.contentType = contentType;
        this.content = content;
        this.status = status;
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "MessageInfo{" +
                "msgId='" + msgId + '\'' +
                ", seq=" + seq +
                ", conversationId='" + conversationId + '\'' +
                ", fromUserId='" + fromUserId + '\'' +
                ", msgType=" + msgType +
                ", contentType=" + contentType +
                ", status=" + status +
                ", createTime=" + createTime +
                '}';
    }
}
// {{END MODIFICATIONS}}
