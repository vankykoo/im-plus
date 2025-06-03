package com.vanky.im.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 
 * @TableName conversation_msg_list
 */
@TableName(value ="conversation_msg_list")
@Data
public class ConversationMsgList {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 
     */
    @TableField(value = "msg_id")
    private Long msgId;

    /**
     * 
     */
    @TableField(value = "seq")
    private Long seq;
}