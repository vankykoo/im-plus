package com.vanky.im.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会话表
 * 
 * @TableName conversation
 */
@TableName(value ="conversation")
@Data
public class Conversation {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
     * 会话ID
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 类型，0私聊，1群聊
     */
    @TableField(value = "type")
    private Integer type;

    /**
     * 会话人数
     */
    @TableField(value = "member_count")
    private Integer memberCount;

    /**
     * 最后一条会话时间
     */
    @TableField(value = "last_msg_time")
    private Date lastMsgTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    private String createBy;

    /**
     * 修改人
     */
    @TableField(value = "update_by")
    private String updateBy;
}