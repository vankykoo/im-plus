package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 私聊消息表
 *
 * @TableName private_message
 */
@TableName(value ="private_message")
@Data
public class PrivateMessage {
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
     * 状态：0-未读，1-已读，2-撤回
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 发送时间
     */
    @TableField(value = "send_time")
    private Date sendTime;
} 