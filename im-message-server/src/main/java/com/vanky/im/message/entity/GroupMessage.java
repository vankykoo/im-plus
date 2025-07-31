package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 群聊消息表
 *
 * @deprecated 该类已被统一的 Message 类替代，请使用 com.vanky.im.message.entity.Message
 * @TableName group_message
 */
@Deprecated
@TableName(value ="group_message")
@Data
public class GroupMessage {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息唯一标识
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
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 消息内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 状态：0-已发送（等待推送），1-推送成功（客户端已确认），2-已读，3-撤回，4-推送失败
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 发送时间
     */
    @TableField(value = "send_time")
    private Date sendTime;
} 