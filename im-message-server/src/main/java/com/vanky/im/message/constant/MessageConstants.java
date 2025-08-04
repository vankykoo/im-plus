package com.vanky.im.message.constant;

/**
 * 消息处理相关常量
 *
 * 更新记录 (2025-08-04 11:44:44 +08:00):
 * - 移除Redis key相关常量，已迁移到im-common模块的RedisKeyConstants类
 * - 保留消息状态、用户状态、好友关系、会话类型等业务常量
 */
public class MessageConstants {

    // ========== 用户状态常量 ==========
    
    /** 用户状态：正常 */
    public static final int USER_STATUS_NORMAL = 1;
    
    /** 用户状态：封禁 */
    public static final int USER_STATUS_BANNED = 2;
    
    /** 用户状态：禁言 */
    public static final int USER_STATUS_MUTED = 3;

    // ========== 好友关系常量 ==========
    
    /** 关系类型：无关系 */
    public static final int RELATIONSHIP_TYPE_NONE = 0;
    
    /** 关系类型：好友 */
    public static final int RELATIONSHIP_TYPE_FRIENDS = 1;
    
    /** 关系类型：用户1拉黑用户2 */
    public static final int RELATIONSHIP_TYPE_USER1_BLOCKED_USER2 = 2;
    
    /** 关系类型：用户2拉黑用户1 */
    public static final int RELATIONSHIP_TYPE_USER2_BLOCKED_USER1 = 3;
    
    /** 关系类型：互相拉黑 */
    public static final int RELATIONSHIP_TYPE_MUTUAL_BLOCKED = 4;

    // ========== 会话类型常量 ==========
    
    /** 会话类型：私聊 */
    public static final int CONVERSATION_TYPE_PRIVATE = 1;
    
    /** 会话类型：群聊 */
    public static final int CONVERSATION_TYPE_GROUP = 2;

    // ========== 会话ID前缀 ==========
    
    /** 私聊会话ID前缀 */
    public static final String PRIVATE_CONVERSATION_PREFIX = "private_";
    
    /** 群聊会话ID前缀 */
    public static final String GROUP_CONVERSATION_PREFIX = "group_";

    // ========== 错误消息 ==========
    
    /** 用户被封禁错误消息 */
    public static final String ERROR_USER_BANNED = "用户被封禁";
    
    /** 用户被禁言错误消息 */
    public static final String ERROR_USER_MUTED = "用户被禁言";
    
    /** 被对方拉黑错误消息 */
    public static final String ERROR_BLOCKED_BY_USER = "被对方拉黑";
    
    /** 空消息占位符 */
    public static final String EMPTY_MESSAGE_PLACEHOLDER = "[空消息]";

    // ========== 消息推送状态常量 ==========

    /** 消息状态：已发送（等待推送） */
    public static final byte MESSAGE_STATUS_SENT = 0;

    /** 消息状态：推送成功（客户端已确认） */
    public static final byte MESSAGE_STATUS_DELIVERED = 1;

    /** 消息状态：已读 */
    public static final byte MESSAGE_STATUS_READ = 2;

    /** 消息状态：撤回 */
    public static final byte MESSAGE_STATUS_RECALLED = 3;

    /** 消息状态：推送失败 */
    public static final byte MESSAGE_STATUS_FAILED = 4;

    // ========== 私有构造函数 ==========
    
    private MessageConstants() {
        // 防止实例化
    }
}
