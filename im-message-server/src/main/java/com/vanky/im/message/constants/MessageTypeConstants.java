package com.vanky.im.message.constants;

/**
 * 消息类型常量定义
 * 
 * @author vanky
 * @since 2025-07-28
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:32:26 +08:00; Reason: 创建消息类型常量类，支持统一的message表结构;
// }}
// {{START MODIFICATIONS}}
public class MessageTypeConstants {
    
    // ==================== 消息类型 ====================
    /**
     * 私聊消息
     */
    public static final byte MSG_TYPE_PRIVATE = 1;

    /**
     * 群聊消息
     */
    public static final byte MSG_TYPE_GROUP = 2;

    /**
     * 群聊消息通知（读扩散模式）
     */
    public static final int GROUP_MESSAGE_NOTIFICATION = 1001;
    
    // ==================== 内容类型 ====================
    /**
     * 文本消息
     */
    public static final byte CONTENT_TYPE_TEXT = 1;

    /**
     * 图片消息
     */
    public static final byte CONTENT_TYPE_IMAGE = 2;

    /**
     * 文件消息
     */
    public static final byte CONTENT_TYPE_FILE = 3;

    /**
     * 语音消息
     */
    public static final byte CONTENT_TYPE_VOICE = 4;

    /**
     * 视频消息
     */
    public static final byte CONTENT_TYPE_VIDEO = 5;

    /**
     * 位置消息
     */
    public static final byte CONTENT_TYPE_LOCATION = 6;

    /**
     * 系统消息
     */
    public static final byte CONTENT_TYPE_SYSTEM = 99;
    
    // ==================== 工具方法 ====================
    
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
}
// {{END MODIFICATIONS}}
