package com.vanky.im.common.enums;

/**
 * 客户端到服务端的消息类型
 *
 * @author vanky
 * @date 2025/5/21
 */
public enum ClientToServerMessageType {

    LOGIN_REQUEST("登录请求", 2001),
    LOGOUT_REQUEST("登出请求", 2002),
    HEARTBEAT("心跳", 2003);

    private final String label;
    private final int value;

    ClientToServerMessageType(String label, int value) {
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
        for (ClientToServerMessageType type : ClientToServerMessageType.values()) {
            if (type.getValue() == value) {
                return type.getLabel();
            }
        }
        return null;
    }
} 