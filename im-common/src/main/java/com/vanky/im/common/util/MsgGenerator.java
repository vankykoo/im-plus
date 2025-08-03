package com.vanky.im.common.util;

import java.util.UUID;

import com.vanky.im.common.constant.MsgContentConstant;
import com.vanky.im.common.constant.ReceiveUserId;
import com.vanky.im.common.constant.MessageTypeConstants;
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
                .setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE)
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
     * @param token 身份验证token
     * @return
     */
    public static ChatMessage generateLoginMsg(String userId, String token) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.LOGIN_REQUEST)
                .setContent(MsgContentConstant.LOGIN_MSG)
                .setFromId(userId)
                .setToId(ReceiveUserId.SYSTEM_ID)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setToken(token)
                .setRetry(0)
                .build();
    }
    
    /**
     * 生成登录消息（无token版本，兼容旧代码）
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateLoginMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.LOGIN_REQUEST)
                .setContent(MsgContentConstant.LOGIN_MSG)
                .setFromId(userId)
                .setToId(ReceiveUserId.SYSTEM_ID)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成退出登录消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateLogoutMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.LOGOUT_REQUEST)
                .setContent(MsgContentConstant.LOGOUT_MSG)
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
                .setType(MessageTypeConstants.KICKOUT_NOTIFICATION)
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
                .setType(MessageTypeConstants.LOGIN_RESPONSE)
                .setContent("登录成功")
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成心跳消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateHeartbeatMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.HEARTBEAT)
                .setContent(MsgContentConstant.HEARTBEAT_PING)
                .setFromId(userId)
                .setToId(ReceiveUserId.SYSTEM_ID)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成心跳响应消息
     *
     * @param userId
     * @return
     */
    public static ChatMessage generateHeartbeatResponseMsg(String userId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.HEARTBEAT)
                .setContent(MsgContentConstant.HEARTBEAT_PONG)
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .build();
    }

    /**
     * 生成消息投递成功响应
     * @param userId 用户ID
     * @param originalMsgId 原始消息ID
     * @return 投递成功响应消息
     */
    public static ChatMessage generateMessageDeliverySuccessMsg(String userId, String originalMsgId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_DELIVERY_SUCCESS)
                .setContent("消息投递成功，原始消息ID: " + originalMsgId)
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis())
                .setSeq(originalMsgId)
                .setRetry(0)
                .build();
    }

    /**
     * 生成消息投递失败响应
     * @param userId 用户ID
     * @param originalMsgId 原始消息ID
     * @param errorMsg 错误信息
     * @return 投递失败响应消息
     */
    public static ChatMessage generateMessageDeliveryFailedMsg(String userId, String originalMsgId, String errorMsg) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_DELIVERY_FAILED)
                .setContent("消息投递失败: " + errorMsg + "，原始消息ID: " + originalMsgId)
                .setFromId(ReceiveUserId.SYSTEM_ID)
                .setToId(userId)
                .setUid(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis())
                .setSeq(originalMsgId)
                .setRetry(0)
                .build();
    }

}
