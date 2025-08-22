package com.vanky.im.testclient.sync;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * 离线消息同步管理器（混合模式）
 * 负责管理整个离线消息同步流程，支持私聊写扩散和群聊读扩散的混合模式并行同步
 *
 * @author vanky
 * @create 2025/7/29
 * @update 2025/8/3 - 支持混合模式并行同步
 */

public class OfflineMessageSyncManager {

    private final HttpClient httpClient;
    private final LocalMessageStorage localStorage;
    private final ExecutorService executorService;
    private final com.vanky.im.testclient.ui.UserWindow userWindow; // 添加UserWindow引用
    private com.vanky.im.testclient.processor.RealtimeMessageProcessor realtimeMessageProcessor;
    
    // 同步状态管理
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private volatile String currentUserId;
    private volatile SyncProgressCallback progressCallback;
    
    // 同步配置
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public OfflineMessageSyncManager(HttpClient httpClient, LocalMessageStorage localStorage, com.vanky.im.testclient.ui.UserWindow userWindow) {
        this.httpClient = httpClient;
        this.localStorage = localStorage;
        this.userWindow = userWindow;
                this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "OfflineMessageSync");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setRealtimeMessageProcessor(com.vanky.im.testclient.processor.RealtimeMessageProcessor processor) {
        this.realtimeMessageProcessor = processor;
    }

    /**
     * 检查并启动同步（如果需要）- 混合模式
     * 这是客户端登录后调用的主入口方法，支持私聊写扩散和群聊读扩散的并行同步
     *
     * @param userId 用户ID
     * @param callback 进度回调（可选）
     */
    public void startSyncIfNeeded(String userId, SyncProgressCallback callback) {
        if (isSyncing.get()) {
            System.out.println("同步已在进行中，跳过本次请求");
            return;
        }

        this.currentUserId = userId;
        this.progressCallback = callback;

        System.out.println("启动混合模式离线消息同步检查 - 用户ID: " + userId);

        // 在后台线程中执行混合模式同步
        CompletableFuture.runAsync(this::performHybridSync, executorService)
                .exceptionally(throwable -> {
                    System.err.println("混合模式离线消息同步异常: " + throwable.getMessage());
                    throwable.printStackTrace();
                    notifyProgress(SyncStatus.ERROR, "同步异常: " + throwable.getMessage());
                    isSyncing.set(false);
                    return null;
                });
    }

    /**
     * 执行混合模式同步流程
     * 并行执行私聊写扩散同步和群聊读扩散同步
     */
    private void performHybridSync() {
        isSyncing.set(true);

        try {
            System.out.println("=== 开始执行混合模式离线消息同步 ===");
            System.out.println("用户ID: " + currentUserId);
            notifyProgress(SyncStatus.STARTED, "开始混合模式同步");

            // 步骤0：初始化群聊同步点（如果需要）
            System.out.println("步骤0: 初始化群聊同步点...");
            initializeGroupSyncPoints();

            // 创建两个并行任务
            AtomicInteger completedTasks = new AtomicInteger(0);
            AtomicBoolean hasError = new AtomicBoolean(false);
            StringBuilder errorMessages = new StringBuilder();

            System.out.println("步骤1: 创建并行同步任务...");

            // 任务一：私聊写扩散同步
            System.out.println("创建任务1: 私聊消息同步（写扩散模式）");
            CompletableFuture<Void> privateTask = CompletableFuture.runAsync(() -> {
                try {
                    System.out.println(">>> 任务1开始：私聊消息同步（写扩散模式）");
                    performPrivateMessageSync();
                    System.out.println(">>> 任务1完成：私聊消息同步");
                } catch (Exception e) {
                    hasError.set(true);
                    errorMessages.append("私聊同步失败: ").append(e.getMessage()).append("; ");
                    System.err.println(">>> 任务1失败：私聊消息同步失败: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    int completed = completedTasks.incrementAndGet();
                    System.out.println(">>> 任务1结束，已完成任务数: " + completed);
                    notifyProgress(SyncStatus.SYNCING, String.format("已完成 %d/2 个同步任务", completed));
                }
            }, executorService);

            // 任务二：群聊读扩散同步
            System.out.println("创建任务2: 群聊消息同步（读扩散模式）");
            CompletableFuture<Void> groupTask = CompletableFuture.runAsync(() -> {
                try {
                    System.out.println(">>> 任务2开始：群聊消息同步（读扩散模式）");
                    performGroupMessageSync();
                    System.out.println(">>> 任务2完成：群聊消息同步");
                } catch (Exception e) {
                    hasError.set(true);
                    errorMessages.append("群聊同步失败: ").append(e.getMessage()).append("; ");
                    System.err.println(">>> 任务2失败：群聊消息同步失败: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    int completed = completedTasks.incrementAndGet();
                    System.out.println(">>> 任务2结束，已完成任务数: " + completed);
                    notifyProgress(SyncStatus.SYNCING, String.format("已完成 %d/2 个同步任务", completed));
                }
            }, executorService);

            // 等待两个任务都完成
            System.out.println("步骤2: 等待两个并行任务完成...");
            CompletableFuture.allOf(privateTask, groupTask).join();
            System.out.println("步骤3: 两个并行任务都已完成");

            // 检查结果
            if (hasError.get()) {
                System.err.println("混合模式同步部分失败: " + errorMessages.toString());
                notifyProgress(SyncStatus.ERROR, "部分同步失败: " + errorMessages.toString());
            } else {
                System.out.println("混合模式同步全部成功");
                notifyProgress(SyncStatus.COMPLETED, "混合模式同步完成");
            }

            System.out.println("=== 混合模式离线消息同步完成 ===");

        } catch (Exception e) {
            System.err.println("混合模式同步过程异常: " + e.getMessage());
            e.printStackTrace();
            notifyProgress(SyncStatus.ERROR, "同步异常: " + e.getMessage());
        } finally {
            isSyncing.set(false);
        }
    }

    /**
     * 执行私聊消息同步（写扩散模式）
     */
    private void performPrivateMessageSync() {
        try {
            // 步骤1：读取本地同步点
            Long lastSyncSeq = localStorage.getLastSyncSeq(currentUserId);
            System.out.println("私聊本地同步点: " + lastSyncSeq);

            // 步骤2：检查是否需要同步
            HttpClient.SyncCheckResponse checkResponse = httpClient.checkSyncNeeded(currentUserId, lastSyncSeq);
            if (checkResponse == null || !checkResponse.isSuccess()) {
                String error = checkResponse != null ? checkResponse.getErrorMessage() : "网络错误";
                System.err.println("私聊同步检查失败: " + error);
                throw new RuntimeException("私聊同步检查失败: " + error);
            }

            if (!checkResponse.isSyncNeeded()) {
                System.out.println("私聊消息无需同步，本地消息已是最新");
                return;
            }

            // 步骤3：执行分页拉取
            Long targetSeq = checkResponse.getTargetSeq();
            Long currentSeq = lastSyncSeq;
            int totalPulled = 0;
            List<String> allMsgIds = new ArrayList<>();

            System.out.println("开始私聊消息拉取 - 目标序列号: " + targetSeq + ", 当前序列号: " + currentSeq);

            while (currentSeq < targetSeq) {
                // 执行一次批量拉取
                PullResult pullResult = pullMessagesBatch(currentSeq + 1);

                if (!pullResult.isSuccess()) {
                    System.err.println("私聊批量拉取失败: " + pullResult.getErrorMessage());
                    throw new RuntimeException("私聊批量拉取失败: " + pullResult.getErrorMessage());
                }

                if (pullResult.getCount() == 0) {
                    break;
                }

                // 更新本地同步点
                currentSeq = pullResult.getMaxSeq();
                localStorage.updateLastSyncSeq(currentUserId, currentSeq);
                totalPulled += pullResult.getCount();

                // 收集消息ID
                allMsgIds.addAll(pullResult.getMsgIds());

                // 显示拉取到的消息
                System.out.println("[DEBUG] 准备显示 " + pullResult.getMessages().size() + " 条私聊消息");
                displayMessages(pullResult.getMessages(), "私聊");

                // 私聊本批次拉取完成

                // 检查是否还有更多消息
                if (!pullResult.isHasMore()) {
                    break;
                }
            }

            // 私聊消息同步完成

            // 发送批量ACK确认
            if (!allMsgIds.isEmpty()) {
                sendBatchAck(allMsgIds);
            }

        } catch (Exception e) {
            System.err.println("私聊消息同步异常: " + e.getMessage());
            throw new RuntimeException("私聊消息同步失败", e);
        }
    }

    /**
     * 执行群聊消息同步（读扩散模式）
     */
    private void performGroupMessageSync() {
        try {
            System.out.println("--- 开始群聊消息同步（读扩散模式） ---");

            // 步骤1：读取本地群聊同步点
            Map<String, Long> conversationSeqMap = localStorage.getConversationSeqMap(currentUserId);

            // 如果没有群聊同步点，说明用户没有群聊，直接返回
            if (conversationSeqMap.isEmpty()) {
                return;
            }

            // 步骤2：拉取群聊消息
            HttpClient.PullGroupMessagesResponse pullResponse = httpClient.pullGroupMessages(
                    currentUserId, conversationSeqMap, 100);

            if (!pullResponse.isSuccess()) {
                System.err.println("群聊消息拉取失败: " + pullResponse.getErrorMessage());
                throw new RuntimeException("群聊消息拉取失败: " + pullResponse.getErrorMessage());
            }

            // 群聊消息拉取完成

            // 步骤2.5：解析并显示群聊消息
            if (pullResponse.getTotalCount() > 0) {
                java.util.List<Object> groupMessages = pullResponse.getMessages(httpClient);
                System.out.println("[DEBUG] 群聊消息解析完成，消息数量: " + groupMessages.size());
                System.out.println("[DEBUG] 群聊消息原始响应: " + pullResponse.getRawResponse());

                if (!groupMessages.isEmpty()) {
                    System.out.println("[DEBUG] 开始显示群聊消息，调用displayMessages");
                    displayMessages(groupMessages, "群聊");
                } else {
                    System.out.println("[DEBUG] 群聊消息列表为空，跳过显示");
                }
            } else {
                System.out.println("[DEBUG] 群聊消息总数为0，无消息需要显示");
            }

            // 步骤3：处理响应并更新同步点
            // 获取服务端返回的最新seq映射
            Map<String, Long> latestSeqMap = pullResponse.getLatestSeqMap();
            System.out.println("[DEBUG] 服务端返回的latestSeqMap: " + latestSeqMap);

            if (latestSeqMap != null && !latestSeqMap.isEmpty()) {
                // 无论是否有新消息，都应该更新本地同步点
                // 这样可以避免重复拉取已经检查过的消息
                localStorage.updateConversationSeqMap(currentUserId, latestSeqMap);
                System.out.println("[DEBUG] 已更新本地群聊同步点，会话数量: " + latestSeqMap.size());

                // 步骤4：发送群聊会话ACK确认（只有当有新消息时才发送ACK）
                if (pullResponse.getTotalCount() > 0) {
                    sendGroupConversationAck(latestSeqMap);
                }
            } else {
                System.out.println("[DEBUG] 服务端未返回latestSeqMap或为空，跳过同步点更新");
            }

            // 群聊消息同步完成

        } catch (Exception e) {
            System.err.println("群聊消息同步异常: " + e.getMessage());
            throw new RuntimeException("群聊消息同步失败", e);
        }
    }

    /**
     * 执行完整的同步流程（保留原有方法，用于向后兼容）
     */
    private void performFullSync() {
        if (!isSyncing.compareAndSet(false, true)) {
            System.out.println("同步已在进行中");
            return;
        }

        try {
            System.out.println("开始执行离线消息同步 - 用户ID: " + currentUserId);
            notifyProgress(SyncStatus.STARTED, "开始同步");

            // 步骤1：读取本地同步点
            Long lastSyncSeq = localStorage.getLastSyncSeq(currentUserId);
            System.out.println("本地同步点: " + lastSyncSeq);

            // 步骤2：检查是否需要同步
            HttpClient.SyncCheckResponse checkResponse = httpClient.checkSyncNeeded(currentUserId, lastSyncSeq);
            if (checkResponse == null || !checkResponse.isSuccess()) {
                String error = checkResponse != null ? checkResponse.getErrorMessage() : "网络错误";
                System.err.println("同步检查失败: " + error);
                notifyProgress(SyncStatus.ERROR, "同步检查失败: " + error);
                return;
            }

            if (!checkResponse.isSyncNeeded()) {
                System.out.println("无需同步，本地消息已是最新");
                notifyProgress(SyncStatus.COMPLETED, "消息已是最新，无需同步");
                return;
            }

            // 步骤3：进入拉取循环
            System.out.println("需要同步消息 - 目标序列号: " + checkResponse.getTargetSeq());
            notifyProgress(SyncStatus.SYNCING, "开始拉取消息");

            long currentSeq = lastSyncSeq;
            long targetSeq = checkResponse.getTargetSeq();
            int totalPulled = 0;
            List<String> allMsgIds = new ArrayList<>(); // 收集所有拉取的消息ID

            while (currentSeq < targetSeq) {
                // 执行一次批量拉取
                PullResult pullResult = pullMessagesBatch(currentSeq + 1);
                
                if (!pullResult.isSuccess()) {
                    System.err.println("批量拉取失败: " + pullResult.getErrorMessage());
                    notifyProgress(SyncStatus.ERROR, "拉取失败: " + pullResult.getErrorMessage());
                    return;
                }

                if (pullResult.getCount() == 0) {
                    System.out.println("本批次无消息，同步完成");
                    break;
                }

                // 更新本地同步点
                currentSeq = pullResult.getMaxSeq();
                localStorage.updateLastSyncSeq(currentUserId, currentSeq);
                totalPulled += pullResult.getCount();

                // 收集消息ID
                allMsgIds.addAll(pullResult.getMsgIds());

                // 显示拉取到的消息
                displayMessages(pullResult.getMessages(), "私聊");

                System.out.println("本批次拉取完成 - 消息数量: " + pullResult.getCount() + 
                                 ", 当前序列号: " + currentSeq + 
                                 ", 累计拉取: " + totalPulled);

                notifyProgress(SyncStatus.SYNCING, 
                             String.format("已拉取 %d 条消息 (%.1f%%)", 
                                         totalPulled, 
                                         (double)(currentSeq - lastSyncSeq) / (targetSeq - lastSyncSeq) * 100));

                // 检查是否还有更多消息
                if (!pullResult.isHasMore()) {
                    System.out.println("所有消息拉取完成");
                    break;
                }
            }

            System.out.println("离线消息同步完成 - 总拉取消息数: " + totalPulled);

            // 发送批量ACK确认
            if (!allMsgIds.isEmpty()) {
                sendBatchAck(allMsgIds);
            }

            notifyProgress(SyncStatus.COMPLETED, "同步完成，共拉取 " + totalPulled + " 条消息");

        } catch (Exception e) {
            System.err.println("同步过程异常: " + e.getMessage());
            e.printStackTrace();
            notifyProgress(SyncStatus.ERROR, "同步异常: " + e.getMessage());
        } finally {
            isSyncing.set(false);
        }
    }

    /**
     * 分页拉取消息
     */
    private PullResult pullMessagesBatch(long fromSeq) {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                HttpClient.PullMessagesResponse response = httpClient.pullMessagesBatch(
                        currentUserId, fromSeq, DEFAULT_BATCH_SIZE);
                
                if (response == null || !response.isSuccess()) {
                    String error = response != null ? response.getErrorMessage() : "网络错误";
                    System.err.println("批量拉取失败 (重试 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + error);
                    
                    if (++retryCount < MAX_RETRY_COUNT) {
                        Thread.sleep(RETRY_DELAY_MS * retryCount); // 指数退避
                        continue;
                    }
                    
                    return new PullResult(false, 0, 0L, false, error, new ArrayList<>(), new ArrayList<>());
                }

                // 这里应该将消息存储到本地数据库
                // 由于简化实现，我们只记录拉取的数量和最大序列号
                long maxSeq = response.getNextSeq() != null ? response.getNextSeq() - 1 : fromSeq + response.getCount() - 1;

                // 收集消息ID用于批量ACK
                List<String> msgIds = new ArrayList<>();
                if (response.getMessages() != null) {
                    for (Object messageObj : response.getMessages()) {
                        // 这里需要根据实际的消息结构提取消息ID
                        // 暂时使用简化的方式
                        if (messageObj instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> messageMap = (java.util.Map<String, Object>) messageObj;
                            String msgId = (String) messageMap.get("msgId");
                            if (msgId != null && !msgId.isEmpty()) {
                                msgIds.add(msgId);
                            }
                        }
                    }
                }

                return new PullResult(true, response.getCount(), maxSeq, response.isHasMore(), null, msgIds, response.getMessages());
                
            } catch (Exception e) {
                System.err.println("批量拉取异常 (重试 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + e.getMessage());
                
                if (++retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new PullResult(false, 0, 0L, false, "线程中断", new ArrayList<>(), new ArrayList<>());
                    }
                } else {
                    return new PullResult(false, 0, 0L, false, "网络异常: " + e.getMessage(), new ArrayList<>(), new ArrayList<>());
                }
            }
        }
        
        return new PullResult(false, 0, 0L, false, "重试次数超限", new ArrayList<>(), new ArrayList<>());
    }

    /**
     * 显示拉取到的消息
     * @param messages 消息列表
     * @param syncType 同步类型
     */
    private void displayMessages(List<Object> messages, String syncType) {
        System.out.println("[DEBUG] displayMessages被调用，消息数量: " + (messages != null ? messages.size() : "null"));
        
        if (messages == null || messages.isEmpty()) {
            System.out.println("[" + syncType + "] 没有消息需要显示");
            return;
        }

        // 同时在控制台和GUI中显示消息，确保用户能看到同步的消息
        System.out.println("[" + syncType + "] 拉取到 " + messages.size() + " 条消息:");
        for (int i = 0; i < messages.size(); i++) {
            Object messageObj = messages.get(i);
            System.out.println("[DEBUG] 处理消息对象[" + i + "]: " + messageObj.getClass().getName());
            
            if (messageObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> messageMap = (java.util.Map<String, Object>) messageObj;
                
                System.out.println("[DEBUG] 消息Map内容: " + messageMap);
                
                String msgId = (String) messageMap.get("msgId");
                String content = (String) messageMap.get("content");
                String fromUserId = (String) messageMap.get("fromUserId");  // 修正字段名
                Object timestamp = messageMap.get("timestamp");

                System.out.println("  [" + (i + 1) + "] 消息ID: " + msgId +
                                 ", 发送者: " + fromUserId +
                                 ", 内容: " + content +
                                 ", 时间: " + timestamp);
            } else {
                System.out.println("[DEBUG] 消息对象不是Map类型: " + messageObj);
                System.out.println("  [" + (i + 1) + "] " + messageObj.toString());
            }
        }
        
        // 同时通过UserWindow显示消息
        if (userWindow != null) {
            System.out.println("[DEBUG] 调用userWindow.displaySyncMessages");
            userWindow.displaySyncMessages(messages, syncType);
        } else {
            System.out.println("[DEBUG] userWindow为null，无法显示到GUI");
        }
    }

    /**
     * 发送批量ACK确认消息
     * @param msgIds 消息ID列表
     */
    private void sendBatchAck(List<String> msgIds) {
        try {
            if (msgIds == null || msgIds.isEmpty()) {
                return;
            }

            System.out.println("发送批量ACK确认 - 消息数量: " + msgIds.size());

            // 将消息ID列表转换为逗号分隔的字符串
            String msgIdsStr = String.join(",", msgIds);

            // 这里需要通过WebSocket或TCP客户端发送批量ACK消息
            // 由于OfflineMessageSyncManager没有直接的客户端引用，
            // 我们需要通过回调或者其他方式来发送消息
            // 暂时先打印日志，实际实现需要在UserWindow中集成

            System.out.println("批量ACK消息内容: " + msgIdsStr);
            System.out.println("注意：需要在UserWindow中集成批量ACK发送逻辑");

        } catch (Exception e) {
            System.err.println("发送批量ACK失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 通知进度回调
     */
    private void notifyProgress(SyncStatus status, String message) {
        if (progressCallback != null) {
            try {
                progressCallback.onProgress(status, message);
            } catch (Exception e) {
                // 忽略进度回调异常
            }
        }
    }

    /**
     * 检查是否正在同步
     */
    public boolean isSyncing() {
        return isSyncing.get();
    }

    /**
     * 停止同步管理器
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * 初始化群聊同步点
     * 通过会话概览同步获取用户的群聊列表，并初始化同步点
     */
    private void initializeGroupSyncPoints() {
        try {
            // 获取当前的群聊同步点
            Map<String, Long> currentSeqMap = localStorage.getConversationSeqMap(currentUserId);

            // 如果已有同步点，直接返回
            if (!currentSeqMap.isEmpty()) {
                return;
            }

            // 调用会话概览同步API获取用户的群聊列表
            HttpClient.ConversationSyncResponse syncResponse = httpClient.syncConversations(currentUserId, 100);
            if (!syncResponse.isSuccess()) {
                return;
            }

            // 解析响应，提取群聊会话ID
            Map<String, Long> newSeqMap = parseGroupConversationsFromResponse(syncResponse.getRawResponse());

            if (!newSeqMap.isEmpty()) {
                // 获取真实的群聊最新序列号
                List<String> conversationIds = new ArrayList<>(newSeqMap.keySet());
                HttpClient.GroupLatestSeqsResponse latestSeqsResponse = httpClient.getGroupLatestSeqs(conversationIds);
                
                if (latestSeqsResponse.isSuccess()) {
                    // 解析真实的序列号
                    Map<String, Long> realSeqMap = parseLatestSeqsFromResponse(latestSeqsResponse.getRawResponse());
                    if (realSeqMap != null && !realSeqMap.isEmpty()) {
                        newSeqMap = realSeqMap;
                    }
                }
                
                localStorage.updateConversationSeqMap(currentUserId, newSeqMap);
            }

        } catch (Exception e) {
            // 初始化失败不影响整体同步流程，继续执行
        }
    }

    /**
     * 从会话概览同步响应中解析群聊会话ID
     * @param rawResponse 原始响应JSON
     * @return 群聊同步点映射，Key为conversationId，Value为初始seq（0）
     */
    private Map<String, Long> parseGroupConversationsFromResponse(String rawResponse) {
        Map<String, Long> groupSeqMap = new HashMap<>();

        try {
            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                return groupSeqMap;
            }

            System.out.println("解析会话概览响应，提取群聊会话...");

            // 简化的JSON解析，查找群聊会话
            // 实际项目中应该使用专业的JSON解析库

            // 查找conversations数组
            int conversationsStart = rawResponse.indexOf("\"conversations\":");
            if (conversationsStart == -1) {
                System.out.println("响应中未找到conversations字段");
                return groupSeqMap;
            }

            // 查找数组开始位置
            int arrayStart = rawResponse.indexOf("[", conversationsStart);
            if (arrayStart == -1) {
                System.out.println("conversations字段不是数组格式");
                return groupSeqMap;
            }

            // 查找数组结束位置
            int arrayEnd = rawResponse.indexOf("]", arrayStart);
            if (arrayEnd == -1) {
                System.out.println("conversations数组格式错误");
                return groupSeqMap;
            }

            String conversationsArray = rawResponse.substring(arrayStart + 1, arrayEnd);

            // 简单解析每个会话对象
            String[] conversations = conversationsArray.split("\\},\\{");
            for (String conversation : conversations) {
                // 清理格式
                conversation = conversation.replace("{", "").replace("}", "");

                // 查找conversationId和conversationType
                String conversationId = extractJsonValue(conversation, "conversationId");
                String conversationType = extractJsonValue(conversation, "conversationType");

                // 只处理群聊会话（type = 1）
                if ("1".equals(conversationType) && conversationId != null && !conversationId.isEmpty()) {
                    groupSeqMap.put(conversationId, 0L); // 初始化为0
                    System.out.println("发现群聊会话: " + conversationId);
                }
            }

            System.out.println("解析完成，发现 " + groupSeqMap.size() + " 个群聊会话");

        } catch (Exception e) {
            System.err.println("解析会话概览响应失败: " + e.getMessage());
            e.printStackTrace();
        }

        return groupSeqMap;
    }

    /**
     * 从JSON字符串中提取指定字段的值
     * @param jsonStr JSON字符串
     * @param fieldName 字段名
     * @return 字段值
     */
    private String extractJsonValue(String jsonStr, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"\\s*:\\s*\"?([^,}\"]+)\"?";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(jsonStr);
            if (m.find()) {
                return m.group(1).trim().replace("\"", "");
            }
        } catch (Exception e) {
            System.err.println("提取JSON字段失败 - 字段: " + fieldName + ", 错误: " + e.getMessage());
        }
        return null;
    }

    /**
     * 从群聊最新序列号响应中解析序列号映射
     * @param rawResponse 原始响应JSON
     * @return 群聊序列号映射，Key为conversationId，Value为最新seq
     */
    private Map<String, Long> parseLatestSeqsFromResponse(String rawResponse) {
        Map<String, Long> seqMap = new HashMap<>();

        try {
            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                return seqMap;
            }

            System.out.println("解析群聊最新序列号响应...");

            // 查找data字段
            int dataStart = rawResponse.indexOf("\"data\":");
            if (dataStart == -1) {
                System.out.println("响应中未找到data字段");
                return seqMap;
            }

            // 查找对象开始位置
            int objStart = rawResponse.indexOf("{", dataStart + 7);
            if (objStart == -1) {
                System.out.println("data字段不是对象格式");
                return seqMap;
            }

            // 查找对象结束位置
            int objEnd = rawResponse.indexOf("}", objStart);
            if (objEnd == -1) {
                System.out.println("data对象格式错误");
                return seqMap;
            }

            String dataObject = rawResponse.substring(objStart + 1, objEnd);

            // 解析键值对
            String[] pairs = dataObject.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String conversationId = kv[0].trim().replace("\"", "");
                    String seqStr = kv[1].trim().replace("\"", "");
                    try {
                        Long seq = Long.parseLong(seqStr);
                        seqMap.put(conversationId, seq);
                        System.out.println("解析群聊序列号: " + conversationId + " -> " + seq);
                    } catch (NumberFormatException e) {
                        System.err.println("序列号格式错误: " + seqStr);
                    }
                }
            }

            System.out.println("解析完成，获取到 " + seqMap.size() + " 个群聊序列号");

        } catch (Exception e) {
            System.err.println("解析群聊最新序列号响应失败: " + e.getMessage());
            e.printStackTrace();
        }

        return seqMap;
    }

    // ========== 内部类定义 ==========

    /**
     * 同步状态枚举
     */
    public enum SyncStatus {
        STARTED,    // 开始同步
        SYNCING,    // 同步中
        COMPLETED,  // 同步完成
        ERROR       // 同步错误
    }

    /**
     * 同步进度回调接口
     */
    public interface SyncProgressCallback {
        void onProgress(SyncStatus status, String message);
    }

    /**
     * 拉取结果内部类
     */
    private static class PullResult {
        private final boolean success;
        private final int count;
        private final long maxSeq;
        private final boolean hasMore;
        private final String errorMessage;
        private final List<String> msgIds; // 新增消息ID列表
        private final List<Object> messages; // 新增消息列表

        public PullResult(boolean success, int count, long maxSeq, boolean hasMore, String errorMessage) {
            this(success, count, maxSeq, hasMore, errorMessage, new ArrayList<>(), new ArrayList<>());
        }

        public PullResult(boolean success, int count, long maxSeq, boolean hasMore, String errorMessage, List<String> msgIds) {
            this(success, count, maxSeq, hasMore, errorMessage, msgIds, new ArrayList<>());
        }

        public PullResult(boolean success, int count, long maxSeq, boolean hasMore, String errorMessage, List<String> msgIds, List<Object> messages) {
            this.success = success;
            this.count = count;
            this.maxSeq = maxSeq;
            this.hasMore = hasMore;
            this.errorMessage = errorMessage;
            this.msgIds = msgIds != null ? msgIds : new ArrayList<>();
            this.messages = messages != null ? messages : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public int getCount() { return count; }
        public long getMaxSeq() { return maxSeq; }
        public boolean isHasMore() { return hasMore; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getMsgIds() { return msgIds; }
        public List<Object> getMessages() { return messages; }
    }

    /**
     * 发送群聊会话ACK确认
     * @param latestSeqMap 会话ID到最新seq的映射
     */
    private void sendGroupConversationAck(Map<String, Long> latestSeqMap) {
        if (latestSeqMap == null || latestSeqMap.isEmpty()) {
            System.out.println("[DEBUG] 没有需要ACK的群聊会话");
            return;
        }

        try {
            // 构建ACK消息内容：conversationId1:seq1,conversationId2:seq2
            StringBuilder contentBuilder = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Long> entry : latestSeqMap.entrySet()) {
                if (!first) {
                    contentBuilder.append(",");
                }
                contentBuilder.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }

            String ackContent = contentBuilder.toString();
            System.out.println("[DEBUG] 发送群聊会话ACK确认 - 内容: " + ackContent);

            // 通过UserWindow的客户端发送ACK消息
            if (userWindow != null) {
                boolean ackSent = userWindow.sendGroupConversationAck(ackContent);
                if (ackSent) {
                    System.out.println("[DEBUG] 群聊会话ACK发送成功");
                } else {
                    System.err.println("[ERROR] 无可用连接发送群聊会话ACK");
                }
            } else {
                System.err.println("[ERROR] UserWindow为空，无法发送群聊会话ACK");
            }

        } catch (Exception e) {
            System.err.println("[ERROR] 发送群聊会话ACK失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * 拉取指定范围内的空洞消息
     * @param conversationId 会话ID
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     */
    public void pullGapMessages(String conversationId, long startSeq, long endSeq) {
        executorService.submit(() -> {
            try {
                System.out.println(String.format("开始拉取空洞消息 - 会话: %s, 范围: [%d, %d]",
                        conversationId, startSeq, endSeq));

                // 复用现有的分页拉取逻辑来模拟空洞消息拉取
                List<Object> pulledObjects = new ArrayList<>();
                long currentPullSeq = startSeq;
                while (currentPullSeq <= endSeq) {
                    PullResult result = pullMessagesBatch(currentPullSeq);
                    if (result.isSuccess() && result.getCount() > 0) {
                        pulledObjects.addAll(result.getMessages());
                        currentPullSeq = result.getMaxSeq() + 1;
                        if (!result.isHasMore()) break;
                    } else {
                        break;
                    }
                }

                System.out.println(String.format("空洞消息拉取完成 - 会话: %s, 范围: [%d, %d], 实际拉取: %d条",
                        conversationId, startSeq, endSeq, pulledObjects.size()));

                if (!pulledObjects.isEmpty() && realtimeMessageProcessor != null) {
                    List<ChatMessage> chatMessages = convertToChatMessages(pulledObjects);
                    realtimeMessageProcessor.processGapMessages(chatMessages);
                }

            } catch (Exception e) {
                System.err.println("拉取空洞消息失败: " + e.getMessage());
            }
        });
    }

    private List<ChatMessage> convertToChatMessages(List<Object> messageObjects) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Object obj : messageObjects) {
            if (obj instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    ChatMessage.Builder builder = ChatMessage.newBuilder();
                    builder.setConversationId((String) map.getOrDefault("conversationId", ""));
                    builder.setConversationSeq(Long.parseLong(map.getOrDefault("seq", "0").toString()));
                    builder.setContent((String) map.getOrDefault("content", ""));
                    builder.setFromId((String) map.getOrDefault("fromUserId", ""));
                    builder.setToId((String) map.getOrDefault("toUserId", ""));
                    builder.setType(((Number) map.getOrDefault("msgType", 0)).intValue());
                    chatMessages.add(builder.build());
                } catch (Exception e) {
                    System.err.println("消息转换失败: " + obj + ", 错误: " + e.getMessage());
                }
            }
        }
        return chatMessages;
    }
}
