package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 会话消息列表
 *
 * @TableName conversation_msg_list
 */
@TableName(value ="conversation_msg_list")
@Data
public class ConversationMsgList {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 消息ID
     */
    @TableField(value = "msg_id")
    private Long msgId;

    /**
     * 消息序号
     */
    @TableField(value = "seq")
    private Long seq;
} 