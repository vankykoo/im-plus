package com.vanky.im.common.constant;

/**
 * 统一的消息类型常量定义
 * 整合所有消息类型，避免冲突，统一管理
 * 
 * @author vanky
 * @since 2025-08-03
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-03 11:15:12 +08:00; Reason: 创建统一的消息类型常量类，解决GROUP_MESSAGE_NOTIFICATION与LOGIN_RESPONSE冲突问题;
// }}
// {{START MODIFICATIONS}}
public class MessageTypeConstants {
    
    // ==================== 客户端到服务端消息 (2000-2999) ====================
    
    /** 登录请求 */
    public static final int LOGIN_REQUEST = 2001;
    
    /** 登出请求 */
    public static final int LOGOUT_REQUEST = 2002;
    
    /** 心跳 */
    public static final int HEARTBEAT = 2003;
    
    /** 消息确认 */
    public static final int MESSAGE_ACK = 2004;
    
    /** 批量消息确认 */
    public static final int BATCH_MESSAGE_ACK = 2005;
    
    // ==================== 服务端到客户端消息 (1000-1999) ====================
    
    /** 登录响应 */
    public static final int LOGIN_RESPONSE = 1001;
    
    /** 踢人通知 */
    public static final int KICKOUT_NOTIFICATION = 1002;
    
    /** 心跳响应 */
    public static final int HEARTBEAT_RESPONSE = 1003;
    
    /** 系统通知 */
    public static final int SYSTEM_NOTIFICATION = 1004;
    
    /** 消息投递成功 */
    public static final int MESSAGE_DELIVERY_SUCCESS = 1005;
    
    /** 消息投递失败 */
    public static final int MESSAGE_DELIVERY_FAILED = 1006;
    
    /** 群聊消息通知（读扩散模式） - 修正冲突，从1001改为1007 */
    public static final int GROUP_MESSAGE_NOTIFICATION = 1007;
    
    // ==================== 客户端到客户端消息 (3000-3999) ====================
    
    /** 私信聊天消息 */
    public static final int PRIVATE_CHAT_MESSAGE = 3001;
    
    /** 群组聊天消息 */
    public static final int GROUP_CHAT_MESSAGE = 3002;
    
    // ==================== 数据库存储消息类型 (byte类型，1-99) ====================
    
    /** 私聊消息 */
    public static final byte MSG_TYPE_PRIVATE = 1;
    
    /** 群聊消息 */
    public static final byte MSG_TYPE_GROUP = 2;
    
    // ==================== 内容类型 (byte类型，1-99) ====================
    
    /** 文本消息 */
    public static final byte CONTENT_TYPE_TEXT = 1;
    
    /** 图片消息 */
    public static final byte CONTENT_TYPE_IMAGE = 2;
    
    /** 文件消息 */
    public static final byte CONTENT_TYPE_FILE = 3;
    
    /** 语音消息 */
    public static final byte CONTENT_TYPE_VOICE = 4;
    
    /** 视频消息 */
    public static final byte CONTENT_TYPE_VIDEO = 5;
    
    /** 位置消息 */
    public static final byte CONTENT_TYPE_LOCATION = 6;
    
    /** 系统消息 */
    public static final byte CONTENT_TYPE_SYSTEM = 99;
    
    // ==================== 工具方法 ====================
    
    /**
     * 根据消息类型值获取描述
     * @param messageType 消息类型值
     * @return 消息类型描述
     */
    public static String getMessageTypeLabel(int messageType) {
        switch (messageType) {
            // 客户端到服务端消息
            case LOGIN_REQUEST: return "登录请求";
            case LOGOUT_REQUEST: return "登出请求";
            case HEARTBEAT: return "心跳";
            case MESSAGE_ACK: return "消息确认";
            case BATCH_MESSAGE_ACK: return "批量消息确认";
            
            // 服务端到客户端消息
            case LOGIN_RESPONSE: return "登录响应";
            case KICKOUT_NOTIFICATION: return "踢人通知";
            case HEARTBEAT_RESPONSE: return "心跳响应";
            case SYSTEM_NOTIFICATION: return "系统通知";
            case MESSAGE_DELIVERY_SUCCESS: return "消息投递成功";
            case MESSAGE_DELIVERY_FAILED: return "消息投递失败";
            case GROUP_MESSAGE_NOTIFICATION: return "群聊消息通知";
            
            // 客户端到客户端消息
            case PRIVATE_CHAT_MESSAGE: return "私信聊天消息";
            case GROUP_CHAT_MESSAGE: return "群组聊天消息";
            
            default: return "未知消息类型";
        }
    }
    
    /**
     * 判断是否为私聊消息
     * @param msgType 消息类型
     * @return true-私聊，false-其他
     */
    public static boolean isPrivateMessage(Byte msgType) {
        return msgType != null && MSG_TYPE_PRIVATE == msgType;
    }
    
    /**
     * 判断是否为群聊消息
     * @param msgType 消息类型
     * @return true-群聊，false-其他
     */
    public static boolean isGroupMessage(Byte msgType) {
        return msgType != null && MSG_TYPE_GROUP == msgType;
    }
    
    /**
     * 获取消息类型描述
     * @param msgType 消息类型
     * @return 类型描述
     */
    public static String getMsgTypeDesc(Byte msgType) {
        if (msgType == null) {
            return "未知";
        }
        switch (msgType) {
            case MSG_TYPE_PRIVATE:
                return "私聊";
            case MSG_TYPE_GROUP:
                return "群聊";
            default:
                return "未知";
        }
    }
    
    /**
     * 获取内容类型描述
     * @param contentType 内容类型
     * @return 类型描述
     */
    public static String getContentTypeDesc(Byte contentType) {
        if (contentType == null) {
            return "未知";
        }
        switch (contentType) {
            case CONTENT_TYPE_TEXT:
                return "文本";
            case CONTENT_TYPE_IMAGE:
                return "图片";
            case CONTENT_TYPE_FILE:
                return "文件";
            case CONTENT_TYPE_VOICE:
                return "语音";
            case CONTENT_TYPE_VIDEO:
                return "视频";
            case CONTENT_TYPE_LOCATION:
                return "位置";
            case CONTENT_TYPE_SYSTEM:
                return "系统";
            default:
                return "未知";
        }
    }
    
    /**
     * 判断是否为聊天消息类型
     * @param messageType 消息类型
     * @return true-聊天消息，false-其他
     */
    public static boolean isChatMessage(int messageType) {
        return messageType == PRIVATE_CHAT_MESSAGE || messageType == GROUP_CHAT_MESSAGE;
    }
    
    /**
     * 判断是否为系统消息类型
     * @param messageType 消息类型
     * @return true-系统消息，false-其他
     */
    public static boolean isSystemMessage(int messageType) {
        return messageType >= 1000 && messageType <= 1999;
    }
    
    /**
     * 判断是否为客户端请求类型
     * @param messageType 消息类型
     * @return true-客户端请求，false-其他
     */
    public static boolean isClientRequest(int messageType) {
        return messageType >= 2000 && messageType <= 2999;
    }
}
// {{END MODIFICATIONS}}
