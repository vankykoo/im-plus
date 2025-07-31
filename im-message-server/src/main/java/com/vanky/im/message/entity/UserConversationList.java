package com.vanky.im.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户-会话表
 *
 * 更新记录 (2025-07-28 22:51:36 +08:00):
 * - 新增 unread_count 字段：未读消息数，用于会话列表显示红点数字
 * - 新增 last_msg_id 字段：最新消息ID，外键关联message表，用于显示最后一条消息摘要
 * - 新增 last_update_time 字段：会话最后更新时间，用于会话列表排序
 *
 * @TableName user_conversation_list
 */
@TableName(value ="user_conversation_list")
@Data
public class UserConversationList {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @TableField(value = "id")
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 会话ID
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 此会话中用户已读的最后一条消息seq
     */
    @TableField(value = "last_read_seq")
    private Long lastReadSeq;

    /**
     * 加入时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * 未读消息数
     */
    @TableField(value = "unread_count")
    private Integer unreadCount;

    /**
     * 此会话最新一条消息的ID (外键关联message表)
     */
    @TableField(value = "last_msg_id")
    private Long lastMsgId;

    /**
     * 会话最后更新时间 (用于排序)
     */
    @TableField(value = "last_update_time")
    private Date lastUpdateTime;
} 