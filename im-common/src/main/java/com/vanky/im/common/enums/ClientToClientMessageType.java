package com.vanky.im.common.enums;

/**
 * 客户端到客户端的消息类型
 *
 * @author vanky
 * @date 2025/5/21
 */
public enum ClientToClientMessageType {

    P2P_CHAT_MESSAGE("私信聊天消息", 3001),
    GROUP_CHAT_MESSAGE("群组聊天消息", 3002);

    private final String label;
    private final int value;

    ClientToClientMessageType(String label, int value) {
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