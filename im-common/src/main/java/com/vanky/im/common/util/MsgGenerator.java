package com.vanky.im.common.util;

import java.util.UUID;

import com.vanky.im.common.constant.ReceiveUserId;
import com.vanky.im.common.enums.ClientToClientMessageType;
import com.vanky.im.common.enums.ClientToServerMessageType;
import com.vanky.im.common.protocol.ChatMessage;

/**
 * @author vanky
 * @create 2025/5/23 22:28
 * @description 消息生成器
 */
public class MsgGenerator {

    /**
     * 生成私聊消息
     *
     * @param fromUserId
     * @param toUserId
     * @param content
     * @return
     */
    public static ChatMessage generatePrivateMsg(String fromUserId, String toUserId, String content) {
        return ChatMessage.newBuilder()
                .setType(ClientToClientMessageType.P2P_CHAT_MESSAGE.getValue())
                .setContent(content)
                .setFromId(fromUserId)
                .setToId(toUserId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成登录消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateLoginMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(ClientToServerMessageType.LOGIN_REQUEST.getValue())
                .setContent("login")
                .setFromId(userId)
                .setToId(ReceiveUserId.SYSTEM_ID)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成踢人通知消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateKickoutMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(ClientToServerMessageType.LOGOUT_REQUEST.getValue())
                .setContent("你已被踢下线，不允许多方登录")
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成登录成功回执消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateLoginSuccessMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(ClientToServerMessageType.LOGIN_REQUEST.getValue())
                .setContent("登录成功")
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

}
