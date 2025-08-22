package com.vanky.im.testclient.processor;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.testclient.storage.LocalMessageStorage;
import com.vanky.im.testclient.sync.OfflineMessageSyncManager;
import com.vanky.im.testclient.ui.UserWindow;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时消息处理器
 * 负责处理实时消息的序列号检查、空洞检测、消息暂存和有序显示
 */
public class RealtimeMessageProcessor {

    private final UserWindow userWindow;
    private final LocalMessageStorage localStorage;
    private final OfflineMessageSyncManager syncManager;

    /**
     * 维护每个会话的期望收到的下一个序列号
     * Key: conversationId
     * Value: expectedConversationSeq
     */
    private final Map<String, Long> expectedSeqMap = new ConcurrentHashMap<>();

    /**
     * 暂存因为序列号不连续而无法立即处理的消息
     * Key: conversationId
     * Value: 按 conversationSeq 排序的优先队列
     */
    private final Map<String, PriorityQueue<ChatMessage>> pendingMessages = new ConcurrentHashMap<>();

    public RealtimeMessageProcessor(UserWindow userWindow, LocalMessageStorage localStorage, OfflineMessageSyncManager syncManager) {
        this.userWindow = userWindow;
        this.localStorage = localStorage;
        this.syncManager = syncManager;
    }

    /**
     * 处理接收到的实时消息
     * @param message 聊天消息
     */
    public synchronized void processMessage(ChatMessage message) {
        String conversationId = message.getConversationId();
        long receivedSeq = message.getConversationSeq();

        // 从本地存储加载或初始化期望的序列号
        long expectedSeq = expectedSeqMap.computeIfAbsent(conversationId,
                cid -> localStorage.getConversationLastSeq(userWindow.getUserId(), cid) + 1);

        if (receivedSeq == expectedSeq) {
            // 序列号连续，直接处理
            displayAndProcess(message);
            expectedSeqMap.put(conversationId, expectedSeq + 1);

            // 检查并处理暂存的后续消息
            processPendingMessages(conversationId);
        } else if (receivedSeq > expectedSeq) {
            // 发现空洞，暂存当前消息，并触发空洞消息拉取
            System.out.println(String.format("发现消息空洞 - 会话: %s, 期望: %d, 收到: %d",
                    conversationId, expectedSeq, receivedSeq));
            addPendingMessage(message);
            // 触发空洞消息拉取
            syncManager.pullGapMessages(conversationId, expectedSeq, receivedSeq - 1);
        } else {
            // 收到重复的旧消息，直接丢弃
            System.out.println(String.format("收到重复消息，直接丢弃 - 会话: %s, 期望: %d, 收到: %d",
                    conversationId, expectedSeq, receivedSeq));
        }
    }

    /**
     * 将消息添加到暂存队列
     */
    private void addPendingMessage(ChatMessage message) {
        pendingMessages.computeIfAbsent(message.getConversationId(),
                k -> new PriorityQueue<>(Comparator.comparingLong(ChatMessage::getConversationSeq)))
                .add(message);
    }

    /**
     * 尝试处理指定会话的暂存消息
     */
    private void processPendingMessages(String conversationId) {
        PriorityQueue<ChatMessage> queue = pendingMessages.get(conversationId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        long expectedSeq = expectedSeqMap.get(conversationId);

        while (!queue.isEmpty() && queue.peek().getConversationSeq() == expectedSeq) {
            ChatMessage message = queue.poll();
            displayAndProcess(message);
            expectedSeq++;
        }

        expectedSeqMap.put(conversationId, expectedSeq);

        if (queue.isEmpty()) {
            pendingMessages.remove(conversationId);
        }
    }

    /**
     * 显示消息并更新本地存储
     */
    private void displayAndProcess(ChatMessage message) {
        // 1. 在UI上显示消息
        userWindow.formatAndDisplayMessage(message);

        // 2. 更新本地存储中的会话序列号
        // localStorage.updateConversationSeq(userWindow.getUserId(), message.getConversationId(), message.getConversationSeq());
    }

    /**
     * 当同步完成后，用于处理拉取到的空洞消息
     * @param gapMessages 拉取到的空洞消息列表
     */
    public synchronized void processGapMessages(List<ChatMessage> gapMessages) {
        if (gapMessages == null || gapMessages.isEmpty()) {
            return;
        }

        // 使用一个Set来记录所有受影响的会话ID
        java.util.Set<String> affectedConversationIds = new java.util.HashSet<>();

        // 对空洞消息按序列号排序
        gapMessages.sort(Comparator.comparingLong(ChatMessage::getConversationSeq));

        for (ChatMessage message : gapMessages) {
            String conversationId = message.getConversationId();
            affectedConversationIds.add(conversationId);
            
            long expectedSeq = expectedSeqMap.getOrDefault(conversationId, 0L);
            if (expectedSeq == 0) {
                // 如果是首次处理该会话，从本地存储加载最新的seq
                expectedSeq = localStorage.getConversationLastSeq(userWindow.getUserId(), conversationId) + 1;
            }


            // 只处理期望的消息，避免重复显示
            if (message.getConversationSeq() == expectedSeq) {
                displayAndProcess(message);
                expectedSeqMap.put(conversationId, expectedSeq + 1);
            }
        }

        // 尝试处理所有受影响会话的暂存消息
        for (String conversationId : affectedConversationIds) {
            processPendingMessages(conversationId);
        }
    }
}