package com.vanky.im.testclient.client;

import com.vanky.im.common.protocol.ChatMessage;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IM 客户端统一接口
 * 定义了所有客户端实现（TCP, WebSocket等）必须提供的核心功能。
 *
 * @author vanky
 * @since 2025-08-23
 */
public interface IMClient {

    /**
     * 连接到服务器。
     */
    void connect();

    /**
     * 断开与服务器的连接。
     */
    void disconnect();

    /**
     * 检查连接是否处于活动状态。
     *
     * @return 如果连接已建立且活动，则返回 true。
     */
    boolean isConnected();

    /**
     * 检查用户是否已登录。
     *
     * @return 如果用户已成功登录，则返回 true。
     */
    boolean isLoggedIn();

    /**
     * 等待连接建立，带有超时。
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 如果在超时时间内连接成功，则返回 true。
     */
    boolean waitForConnection(long timeout, TimeUnit unit);

    /**
     * 设置用户认证令牌。
     *
     * @param token 认证令牌
     */
    void setToken(String token);

    /**
     * 发送私聊消息。
     *
     * @param toUserId 接收方用户ID
     * @param content  消息内容
     */
    void sendPrivateMessage(String toUserId, String content);

    /**
     * 发送群聊消息。
     *
     * @param groupId 群组ID
     * @param content 消息内容
     */
    void sendGroupMessage(String groupId, String content);

    /**
     * 发送已读回执。
     *
     * @param conversationId 会话ID
     * @param lastReadSeq    最后已读消息的序列号
     */
    void sendReadReceipt(String conversationId, long lastReadSeq);

    /**
     * 发送批量ACK确认消息。
     *
     * @param msgIds 待确认的消息ID列表
     */
    void sendBatchAckMessage(List<String> msgIds);

    /**
     * 发送群聊会话ACK确认。
     *
     * @param ackContent ACK内容，格式为 "conversationId1:seq1,conversationId2:seq2"
     */
    void sendGroupConversationAck(String ackContent);

    /**
     * 获取待处理消息的统计信息。
     *
     * @return 统计信息字符串
     */
    String getPendingMessageStatistics();

    /**
     * 消息处理器接口，用于回调上层UI或业务逻辑。
     */
    interface MessageHandler {
        void handleMessage(ChatMessage message);
    }
}