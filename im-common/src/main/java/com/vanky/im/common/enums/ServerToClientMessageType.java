package com.vanky.im.common.enums;

/**
 * 服务端到客户端的消息类型
 *
 * @author vanky
 * @date 2025/5/25
 */
public enum ServerToClientMessageType {

    LOGIN_RESPONSE("登录响应", 1001),
    KICKOUT_NOTIFICATION("踢人通知", 1002),
    HEARTBEAT_RESPONSE("心跳响应", 1003),
    SYSTEM_NOTIFICATION("系统通知", 1004),
    MESSAGE_DELIVERY_SUCCESS("消息投递成功", 1005),
    MESSAGE_DELIVERY_FAILED("消息投递失败", 1006);

    private final String label;
    private final int value;

    ServerToClientMessageType(String label, int value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public int getValue() {
        return value;
    }

    public static String getLabelByValue(int value) {
        for (ServerToClientMessageType type : ServerToClientMessageType.values()) {
            if (type.getValue() == value) {
                return type.getLabel();
            }
        }
        return null;
    }
}