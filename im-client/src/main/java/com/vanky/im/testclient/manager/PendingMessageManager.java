package com.vanky.im.testclient.manager;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.model.MessageStatus;
import com.vanky.im.testclient.model.PendingMessage;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 待确认消息队列管理器
 * 使用简单的定时扫描机制管理待确认消息
 * 
 * @author vanky
 * @create 2025-08-05
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-05 11:50:00 +08:00; Reason: 创建待确认消息队列管理器，使用简单定时扫描实现超时重试;
// }}
// {{START MODIFICATIONS}}
public class PendingMessageManager {
    
    // ========== 配置常量 ==========
    
    /** 默认超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 5000L;
    
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;
    
    /** 定时扫描间隔（秒） */
    private static final int SCAN_INTERVAL_SECONDS = 1;
    
    /** 队列最大容量 */
    private static final int MAX_QUEUE_SIZE = 1000;
    
    /** 消息过期时间（24小时） */
    private static final long MESSAGE_EXPIRE_MS = 24 * 60 * 60 * 1000L;
    
    // ========== 核心组件 ==========
    
    /** 待确认消息映射 */
    private final ConcurrentHashMap<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();
    
    /** 定时任务调度器 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pending-message-scanner");
        t.setDaemon(true);
        return t;
    });
    
    /** 超时回调接口 */
    private TimeoutCallback timeoutCallback;

    /** 本地存储管理器 */
    private LocalMessageStorage localStorage;

    /** 当前用户ID */
    private String userId;

    /** 统计计数器 */
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong timeoutMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    
    /** 是否已启动 */
    private volatile boolean started = false;
    
    // ========== 公共方法 ==========
    
    /**
     * 启动管理器
     */
    public void start() {
        if (started) {
            System.out.println("PendingMessageManager已经启动");
            return;
        }
        
        // 启动定时扫描任务
        scheduler.scheduleAtFixedRate(this::scanTimeoutMessages, 
                SCAN_INTERVAL_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        started = true;
        System.out.println("PendingMessageManager启动成功，扫描间隔: " + SCAN_INTERVAL_SECONDS + "秒");
    }
    
    /**
     * 停止管理器
     */
    public void stop() {
        if (!started) {
            return;
        }
        
        started = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("PendingMessageManager已停止");
    }
    
    /**
     * 添加待确认消息
     * @param message 待确认消息
     * @return 是否添加成功
     */
    public boolean addPendingMessage(PendingMessage message) {
        if (message == null || message.getClientSeq() == null) {
            System.err.println("无效的待确认消息");
            return false;
        }
        
        // 检查队列容量
        if (pendingMessages.size() >= MAX_QUEUE_SIZE) {
            System.err.println("待确认消息队列已满，容量: " + MAX_QUEUE_SIZE);
            return false;
        }
        
        // 添加到队列
        PendingMessage existing = pendingMessages.put(message.getClientSeq(), message);
        if (existing != null) {
            System.out.println("替换已存在的待确认消息: " + message.getClientSeq());
        }
        
        totalMessages.incrementAndGet();
        System.out.println("添加待确认消息: " + message.getClientSeq() + 
                         ", 队列大小: " + pendingMessages.size());
        return true;
    }
    
    /**
     * 处理发送回执（磐石计划：使用conversationSeq替代serverSeq）
     * @param clientSeq 客户端序列号
     * @param serverMsgId 服务端消息ID
     * @param conversationSeq 会话序列号
     * @return 是否处理成功
     */
    public boolean handleSendReceipt(String clientSeq, String serverMsgId, String conversationSeq) {
        if (clientSeq == null) {
            System.err.println("客户端序列号为空");
            return false;
        }

        PendingMessage message = pendingMessages.remove(clientSeq);
        if (message == null) {
            System.out.println("未找到对应的待确认消息: " + clientSeq);
            return false;
        }

        // 更新消息状态
        message.setStatus(MessageStatus.DELIVERED);
        message.setServerMsgId(serverMsgId);
        message.setConversationSeq(conversationSeq); // 磐石计划：使用conversationSeq

        // 根据消息类型更新本地同步点
        updateLocalSyncPoint(message, conversationSeq);

        System.out.println("收到发送回执: " + clientSeq +
                         " -> 服务端消息ID: " + serverMsgId +
                         ", 会话序列号: " + conversationSeq);
        return true;
    }
    
    /**
     * 设置超时回调
     * @param callback 超时回调接口
     */
    public void setTimeoutCallback(TimeoutCallback callback) {
        this.timeoutCallback = callback;
    }

    /**
     * 设置本地存储管理器
     * @param localStorage 本地存储管理器
     */
    public void setLocalStorage(LocalMessageStorage localStorage) {
        this.localStorage = localStorage;
    }

    /**
     * 设置用户ID
     * @param userId 用户ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * 获取队列大小
     * @return 当前队列大小
     */
    public int getQueueSize() {
        return pendingMessages.size();
    }
    
    /**
     * 获取统计信息
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format("总消息数: %d, 超时消息数: %d, 失败消息数: %d, 当前队列大小: %d",
                totalMessages.get(), timeoutMessages.get(), failedMessages.get(), pendingMessages.size());
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 扫描超时消息
     */
    private void scanTimeoutMessages() {
        if (pendingMessages.isEmpty()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // 遍历所有待确认消息
        pendingMessages.entrySet().removeIf(entry -> {
            PendingMessage message = entry.getValue();
            
            // 检查消息是否过期（24小时）
            if (currentTime - message.getSendTime() > MESSAGE_EXPIRE_MS) {
                System.out.println("清理过期消息: " + message.getClientSeq());
                return true;
            }
            
            // 检查是否超时
            if (message.isTimeout(DEFAULT_TIMEOUT_MS)) {
                return handleTimeoutMessage(message);
            }
            
            return false;
        });
    }
    
    /**
     * 处理超时消息
     * @param message 超时消息
     * @return 是否从队列中移除
     */
    private boolean handleTimeoutMessage(PendingMessage message) {
        timeoutMessages.incrementAndGet();
        
        // 检查重试次数
        if (message.getRetryCount() >= MAX_RETRY_COUNT) {
            // 达到最大重试次数，标记为失败
            message.setStatus(MessageStatus.FAILED);
            failedMessages.incrementAndGet();
            
            System.out.println("消息发送失败，达到最大重试次数: " + message.getClientSeq() + 
                             ", 重试次数: " + message.getRetryCount());
            
            // 通知超时回调
            if (timeoutCallback != null) {
                timeoutCallback.onMessageFailed(message);
            }
            
            return true; // 从队列中移除
        } else {
            // 增加重试次数
            message.incrementRetryCount();
            message.updateSendTime();
            
            System.out.println("消息超时，准备重试: " + message.getClientSeq() + 
                             ", 重试次数: " + message.getRetryCount());
            
            // 通知超时回调进行重试
            if (timeoutCallback != null) {
                timeoutCallback.onMessageTimeout(message);
            }
            
            return false; // 保留在队列中
        }
    }
    
    /**
     * 根据消息类型更新本地同步点（磐石计划：使用conversationSeq替代serverSeq）
     * @param message 待确认消息
     * @param conversationSeq 会话序列号
     */
    private void updateLocalSyncPoint(PendingMessage message, String conversationSeq) {
        if (localStorage == null || userId == null) {
            System.out.println("LocalStorage或UserId未设置，跳过同步点更新");
            return;
        }

        if (conversationSeq == null || conversationSeq.trim().isEmpty()) {
            System.err.println("会话序列号为空，无法更新同步点");
            return;
        }

        try {
            Long seq = Long.parseLong(conversationSeq);

            // 根据消息类型更新不同的同步点
            if (message.getMessageType() == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
                // 私聊消息：更新用户级全局同步点
                updatePrivateMessageSyncPoint(seq);
            } else if (message.getMessageType() == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
                // 群聊消息：更新会话级同步点
                updateGroupMessageSyncPoint(message, seq);
            }

        } catch (NumberFormatException e) {
            System.err.println("会话序列号格式错误: " + conversationSeq);
        } catch (Exception e) {
            System.err.println("更新本地同步点失败: " + e.getMessage());
        }
    }

    /**
     * 更新私聊消息的用户级全局同步点
     * @param seq 服务端序列号
     */
    private void updatePrivateMessageSyncPoint(Long seq) {
        try {
            // 获取当前本地同步点
            Long currentSeq = localStorage.getLastSyncSeq(userId);
            if (currentSeq == null) {
                currentSeq = 0L;
            }

            // 只有更大的seq才会更新（单调递增原则）
            if (seq > currentSeq) {
                localStorage.updateLastSyncSeq(userId, seq);
                System.out.println("更新私聊消息同步点成功 - 用户: " + userId +
                                 ", 从 " + currentSeq + " 更新到 " + seq);
            } else {
                System.out.println("私聊消息seq不大于当前同步点，跳过更新 - 当前: " + currentSeq +
                                 ", 新seq: " + seq);
            }
        } catch (Exception e) {
            System.err.println("更新私聊消息同步点失败: " + e.getMessage());
        }
    }

    /**
     * 更新群聊消息的会话级同步点
     * @param message 待确认消息
     * @param seq 服务端序列号
     */
    private void updateGroupMessageSyncPoint(PendingMessage message, Long seq) {
        String conversationId = message.getConversationId();
        if (conversationId == null || conversationId.trim().isEmpty()) {
            System.err.println("群聊消息缺少会话ID，无法更新会话级同步点");
            return;
        }

        try {
            // 使用LocalMessageStorage的updateConversationSeq方法
            // 该方法内部已经实现了单调递增检查
            localStorage.updateConversationSeq(userId, conversationId, seq);
            System.out.println("更新群聊消息同步点成功 - 用户: " + userId +
                             ", 会话: " + conversationId + ", seq: " + seq);
        } catch (Exception e) {
            System.err.println("更新群聊消息同步点失败 - 会话: " + conversationId +
                             ", seq: " + seq + ", 错误: " + e.getMessage());
        }
    }

    // ========== 内部接口 ==========
    
    /**
     * 超时回调接口
     */
    public interface TimeoutCallback {
        
        /**
         * 消息超时回调
         * @param message 超时的消息
         */
        void onMessageTimeout(PendingMessage message);
        
        /**
         * 消息失败回调
         * @param message 失败的消息
         */
        void onMessageFailed(PendingMessage message);
    }
}
// {{END MODIFICATIONS}}
