package com.vanky.im.testclient.pushpull;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 统一消息处理器
 * 实现推拉结合的消息传输模式，负责处理接收到的消息，检查序列号连续性，
 * 触发拉取补偿机制，并维护乱序缓冲区
 * 
 * @author vanky
 * @since 2025-08-05
 */
public class UnifiedMessageProcessor {
    
    private static final Logger log = Logger.getLogger(UnifiedMessageProcessor.class.getName());
    
    /** 乱序缓冲区映射：会话ID -> 缓冲区 */
    private final Map<String, OutOfOrderBuffer> buffers = new ConcurrentHashMap<>();
    
    /** 消息空洞检测器 */
    private final MessageGapDetector gapDetector;
    
    /** 拉取补偿管理器 */
    private final PullCompensationManager pullManager;
    
    /** 本地存储 */
    private final LocalMessageStorage localStorage;
    
    /** 当前用户ID */
    private final String userId;
    
    /** 消息处理回调接口 */
    private MessageProcessCallback messageCallback;
    
    /**
     * 构造函数
     * 
     * @param httpClient HTTP客户端
     * @param localStorage 本地存储
     * @param userId 用户ID
     */
    public UnifiedMessageProcessor(HttpClient httpClient, LocalMessageStorage localStorage, String userId) {
        this.localStorage = localStorage;
        this.userId = userId;
        this.gapDetector = new MessageGapDetector();
        this.pullManager = new PullCompensationManager(httpClient, localStorage, userId);
        
        log.info("创建统一消息处理器 - 用户ID: " + userId);
    }
    
    /**
     * 设置消息处理回调
     * 
     * @param callback 回调接口
     */
    public void setMessageCallback(MessageProcessCallback callback) {
        this.messageCallback = callback;
    }
    
    /**
     * 处理接收到的消息
     * 这是推拉结合模式的核心入口方法
     * 
     * @param message 接收到的消息
     */
    public void processReceivedMessage(ChatMessage message) {
        try {
            log.info("开始处理接收消息 - 消息ID: " + message.getUid() + ", 类型: " + message.getType());
            
            // 提取序列号和会话ID
            MessageSeqInfo seqInfo = extractSequenceInfo(message);
            if (seqInfo == null) {
                log.warning("无法提取序列号信息，跳过消息处理 - 消息ID: " + message.getUid());
                return;
            }
            
            // 获取本地序列号
            long localSeq = getLocalSequence(seqInfo.conversationId, seqInfo.isPrivateChat);
            
            // 检查消息状态
            MessageGapDetector.MessageStatus status = gapDetector.getMessageStatus(seqInfo.messageSeq, localSeq);
            
            switch (status) {
                case SEQUENTIAL:
                    // 连续消息，立即处理
                    processSequentialMessage(message, seqInfo);
                    break;
                    
                case OUT_OF_ORDER:
                    // 乱序消息，检测空洞并缓存
                    processOutOfOrderMessage(message, seqInfo, localSeq);
                    break;
                    
                case DUPLICATE_OR_EXPIRED:
                    // 重复或过期消息，忽略
                    log.info("忽略重复或过期消息 - 消息ID: " + message.getUid() + 
                            ", 消息序列号: " + seqInfo.messageSeq + ", 本地序列号: " + localSeq);
                    break;
                    
                case INVALID:
                    // 无效消息，记录错误
                    log.warning("无效消息序列号 - 消息ID: " + message.getUid() + 
                            ", 消息序列号: " + seqInfo.messageSeq + ", 本地序列号: " + localSeq);
                    break;
            }
            
        } catch (Exception e) {
            log.severe("处理消息异常 - 消息ID: " + message.getUid() + ", 异常: " + e.getMessage());
        }
    }
    
    /**
     * 处理连续消息
     * 
     * @param message 消息
     * @param seqInfo 序列号信息
     */
    private void processSequentialMessage(ChatMessage message, MessageSeqInfo seqInfo) {
        log.info("处理连续消息 - 消息ID: " + message.getUid() + ", 序列号: " + seqInfo.messageSeq);
        
        // 立即渲染消息
        renderMessage(message);
        
        // 更新本地序列号
        updateLocalSequence(seqInfo.conversationId, seqInfo.messageSeq, seqInfo.isPrivateChat);
        
        // 检查乱序缓冲区是否有可以处理的消息
        processBufferedMessages(seqInfo.conversationId);
    }
    
    /**
     * 处理乱序消息
     * 
     * @param message 消息
     * @param seqInfo 序列号信息
     * @param localSeq 本地序列号
     */
    private void processOutOfOrderMessage(ChatMessage message, MessageSeqInfo seqInfo, long localSeq) {
        log.info("处理乱序消息 - 消息ID: " + message.getUid() + 
                ", 消息序列号: " + seqInfo.messageSeq + ", 本地序列号: " + localSeq);
        
        // 检测消息空洞
        MessageGapDetector.GapRange gapRange = gapDetector.getGapRange(seqInfo.messageSeq, localSeq);
        if (gapRange != null && gapRange.isValid()) {
            // 触发拉取补偿
            triggerPullCompensation(seqInfo.conversationId, gapRange, seqInfo.isPrivateChat);
        }
        
        // 将消息添加到乱序缓冲区
        OutOfOrderBuffer buffer = getOrCreateBuffer(seqInfo.conversationId, localSeq + 1);
        buffer.addMessage(message, seqInfo.messageSeq);
    }
    
    /**
     * 触发拉取补偿
     * 
     * @param conversationId 会话ID
     * @param gapRange 空洞范围
     * @param isPrivateChat 是否私聊
     */
    private void triggerPullCompensation(String conversationId, MessageGapDetector.GapRange gapRange, boolean isPrivateChat) {
        log.info("触发拉取补偿 - 会话ID: " + conversationId + ", 空洞范围: " + gapRange + 
                ", 是否私聊: " + isPrivateChat);
        
        PullCompensationManager.CompensationCallback callback = new PullCompensationManager.CompensationCallback() {
            @Override
            public void onSuccess(int pulledCount, List<ChatMessage> pulledMessages) {
                log.info("拉取补偿成功 - 会话ID: " + conversationId + ", 拉取数量: " + pulledCount);

                // 处理拉取到的补偿消息
                if (pulledMessages != null && !pulledMessages.isEmpty()) {
                    processPulledMessages(pulledMessages, conversationId, isPrivateChat);
                }

                // 拉取补偿成功后，更新本地序列号到空洞结束位置
                updateLocalSequence(conversationId, gapRange.getToSeq(), isPrivateChat);

                // 检查缓冲区是否有可以处理的消息
                processBufferedMessages(conversationId);
            }

            @Override
            public void onFailure(String errorMessage) {
                log.warning("拉取补偿失败 - 会话ID: " + conversationId + ", 错误: " + errorMessage);
                // 拉取失败时，可以考虑重试或者记录失败状态
                // 这里暂时只记录日志
            }
        };
        
        if (isPrivateChat) {
            pullManager.triggerPrivatePullCompensation(gapRange.getFromSeq(), gapRange.getToSeq(), callback);
        } else {
            pullManager.triggerGroupPullCompensation(conversationId, gapRange.getFromSeq(), gapRange.getToSeq(), callback);
        }
    }
    
    /**
     * 处理缓冲区中的消息
     *
     * @param conversationId 会话ID
     */
    private void processBufferedMessages(String conversationId) {
        OutOfOrderBuffer buffer = buffers.get(conversationId);
        if (buffer != null) {
            List<ChatMessage> orderedMessages = buffer.getOrderedMessages();
            for (ChatMessage message : orderedMessages) {
                log.info("处理缓冲区消息 - 消息ID: " + message.getUid());
                renderMessage(message);

                // 更新本地序列号
                MessageSeqInfo seqInfo = extractSequenceInfo(message);
                if (seqInfo != null) {
                    updateLocalSequence(seqInfo.conversationId, seqInfo.messageSeq, seqInfo.isPrivateChat);
                }
            }

            // 如果还有消息可以处理，递归调用
            if (!orderedMessages.isEmpty()) {
                processBufferedMessages(conversationId);
            }
        }
    }

    /**
     * 处理拉取到的补偿消息
     * 这些消息应该按序插入到本地序列号中
     *
     * @param messages 拉取到的消息列表
     * @param conversationId 会话ID
     * @param isPrivateChat 是否私聊
     */
    public void processPulledMessages(List<ChatMessage> messages, String conversationId, boolean isPrivateChat) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        log.info("开始处理拉取到的补偿消息 - 会话ID: " + conversationId + ", 消息数量: " + messages.size());

        // 按序处理拉取到的消息
        for (ChatMessage message : messages) {
            log.info("处理拉取补偿消息 - 消息ID: " + message.getUid());

            // 直接渲染消息（拉取到的消息已经是按序的）
            renderMessage(message);

            // 更新本地序列号
            MessageSeqInfo seqInfo = extractSequenceInfo(message);
            if (seqInfo != null) {
                updateLocalSequence(seqInfo.conversationId, seqInfo.messageSeq, seqInfo.isPrivateChat);
            }
        }

        log.info("拉取补偿消息处理完成 - 会话ID: " + conversationId);
    }
    
    /**
     * 渲染消息
     *
     * @param message 消息
     */
    private void renderMessage(ChatMessage message) {
        // 统一推送逻辑：检查是否为自己发送的消息，如果是则作为发送确认处理
        if (userId != null && userId.equals(message.getFromId())) {
            // 处理自己发送的消息作为发送确认
            handleSelfSentMessage(message);
            return; // 不在UI中显示自己发送的消息
        }

        // 处理其他用户发送的消息，正常显示
        if (messageCallback != null) {
            messageCallback.onMessageReceived(message);
        } else {
            // 默认处理：简单打印
            System.out.println("收到消息: " + message.getContent());
        }
    }
    
    /**
     * 提取序列号信息
     * 
     * @param message 消息
     * @return 序列号信息
     */
    private MessageSeqInfo extractSequenceInfo(ChatMessage message) {
        // 判断是否为私聊消息
        boolean isPrivateChat = isPrivateChatMessage(message);
        
        long messageSeq;
        String conversationId;
        
        if (isPrivateChat) {
            // 私聊消息使用用户级全局序列号
            messageSeq = message.getUserSeq();
            conversationId = generatePrivateChatConversationId(message.getFromId(), message.getToId());
        } else {
            // 群聊消息使用会话级序列号
            messageSeq = message.getConversationSeq();
            conversationId = message.getConversationId();
        }
        
        if (messageSeq <= 0 || conversationId == null || conversationId.isEmpty()) {
            return null;
        }
        
        return new MessageSeqInfo(messageSeq, conversationId, isPrivateChat);
    }
    
    /**
     * 判断是否为私聊消息
     * 
     * @param message 消息
     * @return 是否私聊
     */
    private boolean isPrivateChatMessage(ChatMessage message) {
        // 根据消息类型或会话ID格式判断
        // 这里使用简单的判断逻辑：如果conversationId为空或者以"private_"开头，则认为是私聊
        String conversationId = message.getConversationId();
        return conversationId == null || conversationId.isEmpty() || conversationId.startsWith("private_");
    }
    
    /**
     * 生成私聊会话ID
     * 
     * @param fromId 发送方ID
     * @param toId 接收方ID
     * @return 会话ID
     */
    private String generatePrivateChatConversationId(String fromId, String toId) {
        // 确保会话ID的一致性，较小的ID在前
        if (fromId.compareTo(toId) < 0) {
            return "private_" + fromId + "_" + toId;
        } else {
            return "private_" + toId + "_" + fromId;
        }
    }
    
    /**
     * 获取本地序列号
     *
     * @param conversationId 会话ID
     * @param isPrivateChat 是否私聊
     * @return 本地序列号
     */
    private long getLocalSequence(String conversationId, boolean isPrivateChat) {
        if (isPrivateChat) {
            return localStorage.getLastSyncSeq(userId);
        } else {
            return localStorage.getConversationLastSeq(userId, conversationId);
        }
    }
    
    /**
     * 更新本地序列号
     *
     * @param conversationId 会话ID
     * @param newSeq 新序列号
     * @param isPrivateChat 是否私聊
     */
    private void updateLocalSequence(String conversationId, long newSeq, boolean isPrivateChat) {
        if (isPrivateChat) {
            localStorage.updateLastSyncSeq(userId, newSeq);
        } else {
            localStorage.updateConversationLastSeq(userId, conversationId, newSeq);
        }

        log.info("更新本地序列号 - 会话ID: " + conversationId + ", 新序列号: " + newSeq + ", 是否私聊: " + isPrivateChat);
    }
    
    /**
     * 获取或创建乱序缓冲区
     * 
     * @param conversationId 会话ID
     * @param expectedSeq 期望序列号
     * @return 缓冲区
     */
    private OutOfOrderBuffer getOrCreateBuffer(String conversationId, long expectedSeq) {
        return buffers.computeIfAbsent(conversationId, k -> new OutOfOrderBuffer(conversationId, expectedSeq));
    }
    
    /**
     * 关闭处理器
     */
    public void shutdown() {
        log.info("关闭统一消息处理器 - 用户ID: " + userId);
        
        // 清空所有缓冲区
        for (OutOfOrderBuffer buffer : buffers.values()) {
            buffer.clear();
        }
        buffers.clear();
        
        // 关闭拉取补偿管理器
        pullManager.shutdown();
    }
    
    /**
     * 序列号信息
     */
    private static class MessageSeqInfo {
        final long messageSeq;
        final String conversationId;
        final boolean isPrivateChat;
        
        MessageSeqInfo(long messageSeq, String conversationId, boolean isPrivateChat) {
            this.messageSeq = messageSeq;
            this.conversationId = conversationId;
            this.isPrivateChat = isPrivateChat;
        }
    }
    
    /**
     * 处理自己发送的消息（统一推送理念：发送方也是接收方）
     *
     * @param message 自己发送的消息
     */
    private void handleSelfSentMessage(ChatMessage message) {
        try {
            log.info("接收到自己发送的消息 - 消息ID: " + message.getUid() +
                    ", 消息类型: " + message.getType() + ", 客户端序列号: " + message.getClientSeq());

            // 检查是否包含完整的消息字段（磐石计划：使用conversationSeq替代serverSeq）
            String clientSeq = message.getClientSeq();
            String uid = message.getUid();
            long conversationSeq = message.getConversationSeq();

            if (clientSeq != null && !clientSeq.trim().isEmpty() &&
                uid != null && !uid.trim().isEmpty() &&
                conversationSeq > 0) {

                // 通知待确认消息管理器更新消息状态
                if (messageDeliveryCallback != null) {
                    boolean success = messageDeliveryCallback.onMessageDelivered(clientSeq, uid, String.valueOf(conversationSeq));
                    if (success) {
                        log.info("消息状态更新成功 - 客户端序列号: " + clientSeq +
                                ", 服务端消息ID: " + uid + ", 会话序列号: " + conversationSeq);
                    } else {
                        log.warning("消息状态更新失败 - 客户端序列号: " + clientSeq);
                    }
                } else {
                    log.warning("消息投递回调未设置，无法更新消息状态");
                }
            } else {
                log.info("消息不包含完整的字段，跳过状态更新 - 消息ID: " + message.getUid());
            }

        } catch (Exception e) {
            log.warning("处理自己发送的消息失败 - 消息ID: " + message.getUid() + ", 错误: " + e.getMessage());
        }
    }

    /** 消息投递回调接口 */
    private MessageDeliveryCallback messageDeliveryCallback;

    /**
     * 设置消息投递回调
     *
     * @param callback 消息投递回调
     */
    public void setMessageDeliveryCallback(MessageDeliveryCallback callback) {
        this.messageDeliveryCallback = callback;
    }

    /**
     * 消息投递回调接口（统一推送理念：发送方接收到自己的消息时更新状态）
     */
    public interface MessageDeliveryCallback {
        /**
         * 消息投递回调
         *
         * @param clientSeq 客户端序列号
         * @param uid 服务端生成的全局唯一消息ID
         * @param serverSeq 服务端序列号
         * @return 是否处理成功
         */
        boolean onMessageDelivered(String clientSeq, String uid, String serverSeq);
    }

    /**
     * 消息处理回调接口
     */
    public interface MessageProcessCallback {
        /**
         * 消息接收回调
         *
         * @param message 接收到的消息
         */
        void onMessageReceived(ChatMessage message);
    }
}
