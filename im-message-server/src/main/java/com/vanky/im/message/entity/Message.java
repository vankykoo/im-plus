package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 统一消息表
 * 合并了原来的private_message和group_message表
 *
 * @TableName message
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:32:26 +08:00; Reason: 创建统一的Message实体类，对应合并后的message表;
// }}
// {{START MODIFICATIONS}}
@TableName(value = "message")
@Data
public class Message {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 全局唯一的消息ID (由雪花算法等生成)
     */
    @TableField(value = "msg_id")
    private Long msgId;

    /**
     * 会话ID
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 发送者用户ID
     */
    @TableField(value = "sender_id")
    private Long senderId;

    /**
     * 消息类型：1-私聊，2-群聊
     */
    @TableField(value = "msg_type")
    private Byte msgType;

    /**
     * 内容类型：1-文本，2-图片，3-文件，4-语音，5-视频，6-位置，99-系统
     */
    @TableField(value = "content_type")
    private Byte contentType;

    /**
     * 消息内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 消息状态：0-已发送（等待推送），1-推送成功（客户端已确认），2-已读，3-撤回，4-推送失败
     */
    @TableField(value = "status")
    private Byte status;

    /**
     * 发送时间
     */
    @TableField(value = "send_time")
    private Date sendTime;

    /**
     * 修改时间
     */
    @TableField(value = "update_time")
    private Date updateTime;
}
// {{END MODIFICATIONS}}
