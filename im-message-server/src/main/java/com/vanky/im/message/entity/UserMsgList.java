package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户消息列表
 *
 * @TableName user_msg_list
 */
@TableName(value ="user_msg_list")
@Data
public class UserMsgList {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 消息ID
     */
    @TableField(value = "msg_id")
    private Long msgId;

    /**
     * 会话ID
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 消息序号
     */
    @TableField(value = "seq")
    private Long seq;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;
} 