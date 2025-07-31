package com.vanky.im.testclient.sync;

import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;

/**
 * 离线消息同步管理器
 * 负责管理整个离线消息同步流程，在后台异步执行
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建离线消息同步管理器，实现客户端的消息内容同步功能;
// }}
// {{START MODIFICATIONS}}
public class OfflineMessageSyncManager {

    private final HttpClient httpClient;
    private final LocalMessageStorage localStorage;
    private final ExecutorService executorService;
    
    // 同步状态管理
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private volatile String currentUserId;
    private volatile SyncProgressCallback progressCallback;
    
    // 同步配置
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public OfflineMessageSyncManager(HttpClient httpClient, LocalMessageStorage localStorage) {
        this.httpClient = httpClient;
        this.localStorage = localStorage;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "OfflineMessageSync");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 检查并启动同步（如果需要）
     * 这是客户端登录后调用的主入口方法
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

        System.out.println("启动离线消息同步检查 - 用户ID: " + userId);

        // 在后台线程中执行同步
        CompletableFuture.runAsync(this::performFullSync, executorService)
                .exceptionally(throwable -> {
                    System.err.println("离线消息同步异常: " + throwable.getMessage());
                    throwable.printStackTrace();
                    notifyProgress(SyncStatus.ERROR, "同步异常: " + throwable.getMessage());
                    isSyncing.set(false);
                    return null;
                });
    }

    /**
     * 执行完整的同步流程
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
                    
                    return new PullResult(false, 0, 0L, false, error);
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

                return new PullResult(true, response.getCount(), maxSeq, response.isHasMore(), null, msgIds);
                
            } catch (Exception e) {
                System.err.println("批量拉取异常 (重试 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + e.getMessage());
                
                if (++retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new PullResult(false, 0, 0L, false, "线程中断");
                    }
                } else {
                    return new PullResult(false, 0, 0L, false, "网络异常: " + e.getMessage());
                }
            }
        }
        
        return new PullResult(false, 0, 0L, false, "重试次数超限");
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
                System.err.println("进度回调异常: " + e.getMessage());
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

        public PullResult(boolean success, int count, long maxSeq, boolean hasMore, String errorMessage) {
            this(success, count, maxSeq, hasMore, errorMessage, new ArrayList<>());
        }

        public PullResult(boolean success, int count, long maxSeq, boolean hasMore, String errorMessage, List<String> msgIds) {
            this.success = success;
            this.count = count;
            this.maxSeq = maxSeq;
            this.hasMore = hasMore;
            this.errorMessage = errorMessage;
            this.msgIds = msgIds != null ? msgIds : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public int getCount() { return count; }
        public long getMaxSeq() { return maxSeq; }
        public boolean isHasMore() { return hasMore; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getMsgIds() { return msgIds; }
    }
}
// {{END MODIFICATIONS}}
