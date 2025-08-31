package com.vanky.im.testclient.pushpull;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.testclient.client.HttpClient;
import com.vanky.im.testclient.storage.LocalMessageStorage;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * 拉取补偿管理器
 * 当检测到消息空洞时，触发拉取补偿机制，从服务端拉取缺失的消息
 *
 * @author vanky
 * @since 2025-08-05
 */
public class PullCompensationManager {

    private static final Logger log = Logger.getLogger(PullCompensationManager.class.getName());
    
    /** HTTP客户端，用于调用拉取API */
    private final HttpClient httpClient;
    
    /** 本地存储，用于更新同步点 */
    private final LocalMessageStorage localStorage;
    
    /** 当前用户ID */
    private final String userId;
    
    /** 异步执行器 */
    private final ExecutorService executorService;
    
    /** 是否正在执行拉取补偿 */
    private final AtomicBoolean isPulling = new AtomicBoolean(false);
    
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;
    
    /** 重试延迟（毫秒） */
    private static final long RETRY_DELAY_MS = 1000;
    
    /** 批量拉取大小 */
    private static final int BATCH_SIZE = 50;
    
    /**
     * 构造函数
     * 
     * @param httpClient HTTP客户端
     * @param localStorage 本地存储
     * @param userId 用户ID
     */
    public PullCompensationManager(HttpClient httpClient, LocalMessageStorage localStorage, String userId) {
        this.httpClient = httpClient;
        this.localStorage = localStorage;
        this.userId = userId;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "PullCompensation-" + userId);
            thread.setDaemon(true);
            return thread;
        });
        
        log.info("创建拉取补偿管理器 - 用户ID: " + userId);
    }
    
    /**
     * 触发私聊消息拉取补偿
     * 
     * @param fromSeq 起始序列号（包含）
     * @param toSeq 结束序列号（包含）
     * @param callback 完成回调
     */
    public void triggerPrivatePullCompensation(long fromSeq, long toSeq, CompensationCallback callback) {
        if (isPulling.get()) {
            log.warning("拉取补偿正在进行中，跳过本次请求 - 用户ID: " + userId);
            if (callback != null) {
                callback.onFailure("拉取补偿正在进行中");
            }
            return;
        }

        log.info("触发私聊消息拉取补偿 - 用户ID: " + userId + ", 序列号范围: [" + fromSeq + ", " + toSeq + "]");
        
        // 异步执行拉取补偿
        CompletableFuture.runAsync(() -> {
            executePrivatePullCompensation(fromSeq, toSeq, callback);
        }, executorService);
    }
    
    /**
     * 触发群聊消息拉取补偿
     * 
     * @param conversationId 会话ID
     * @param fromSeq 起始序列号（包含）
     * @param toSeq 结束序列号（包含）
     * @param callback 完成回调
     */
    public void triggerGroupPullCompensation(String conversationId, long fromSeq, long toSeq, CompensationCallback callback) {
        if (isPulling.get()) {
            log.warning("拉取补偿正在进行中，跳过本次请求 - 用户ID: " + userId + ", 会话ID: " + conversationId);
            if (callback != null) {
                callback.onFailure("拉取补偿正在进行中");
            }
            return;
        }

        log.info("触发群聊消息拉取补偿 - 用户ID: " + userId + ", 会话ID: " + conversationId +
                ", 序列号范围: [" + fromSeq + ", " + toSeq + "]");
        
        // 异步执行拉取补偿
        CompletableFuture.runAsync(() -> {
            executeGroupPullCompensation(conversationId, fromSeq, toSeq, callback);
        }, executorService);
    }
    
    /**
     * 执行私聊消息拉取补偿
     */
    private void executePrivatePullCompensation(long fromSeq, long toSeq, CompensationCallback callback) {
        if (!isPulling.compareAndSet(false, true)) {
            log.warning("拉取补偿已在进行中 - 用户ID: " + userId);
            if (callback != null) {
                callback.onFailure("拉取补偿已在进行中");
            }
            return;
        }

        try {
            log.info("开始执行私聊消息拉取补偿 - 用户ID: " + userId + ", 序列号范围: [" + fromSeq + ", " + toSeq + "]");
            
            int totalPulled = 0;
            long currentSeq = fromSeq;
            
            while (currentSeq <= toSeq) {
                // 计算本批次拉取的结束序列号
                long batchEndSeq = Math.min(currentSeq + BATCH_SIZE - 1, toSeq);
                
                // 执行批量拉取
                PullResult result = pullPrivateMessagesBatch(currentSeq, batchEndSeq);
                
                if (!result.isSuccess()) {
                    log.severe("私聊消息拉取补偿失败 - 用户ID: " + userId + ", 序列号范围: [" + currentSeq + ", " + batchEndSeq + "], 错误: " + result.getErrorMessage());
                    if (callback != null) {
                        callback.onFailure(result.getErrorMessage());
                    }
                    return;
                }

                // 处理拉取到的消息
                if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                    totalPulled += result.getMessages().size();

                    // 更新本地同步点
                    localStorage.updateLastSyncSeq(userId, batchEndSeq);

                    log.info("私聊消息拉取补偿批次完成 - 用户ID: " + userId + ", 序列号范围: [" + currentSeq + ", " + batchEndSeq + "], 拉取数量: " + result.getMessages().size());
                }
                
                // 移动到下一批次
                currentSeq = batchEndSeq + 1;
            }
            
            log.info("私聊消息拉取补偿完成 - 用户ID: " + userId + ", 总拉取数量: " + totalPulled);

            if (callback != null) {
                // 注意：这里传递空的消息列表，因为消息已经在批次处理中处理了
                // 实际项目中可能需要收集所有拉取到的消息再一起返回
                callback.onSuccess(totalPulled, new ArrayList<>());
            }

        } catch (Exception e) {
            log.severe("私聊消息拉取补偿异常 - 用户ID: " + userId + ", 异常: " + e.getMessage());
            if (callback != null) {
                callback.onFailure("拉取补偿异常: " + e.getMessage());
            }
        } finally {
            isPulling.set(false);
        }
    }
    
    /**
     * 执行群聊消息拉取补偿
     */
    private void executeGroupPullCompensation(String conversationId, long fromSeq, long toSeq, CompensationCallback callback) {
        if (!isPulling.compareAndSet(false, true)) {
            log.warning("拉取补偿已在进行中 - 用户ID: " + userId + ", 会话ID: " + conversationId);
            if (callback != null) {
                callback.onFailure("拉取补偿已在进行中");
            }
            return;
        }

        try {
            log.info("开始执行群聊消息拉取补偿 - 用户ID: " + userId + ", 会话ID: " + conversationId +
                    ", 序列号范围: [" + fromSeq + ", " + toSeq + "]");

            // 执行群聊消息拉取补偿
            PullResult result = pullGroupMessagesBatch(conversationId, fromSeq, toSeq);

            if (!result.isSuccess()) {
                log.severe("群聊消息拉取补偿失败 - 用户ID: " + userId + ", 会话ID: " + conversationId +
                        ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 错误: " + result.getErrorMessage());
                if (callback != null) {
                    callback.onFailure(result.getErrorMessage());
                }
                return;
            }

            // 处理拉取到的消息
            int totalPulled = 0;
            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                totalPulled = result.getMessages().size();

                // 更新本地同步点
                localStorage.updateConversationSeq(userId, conversationId, toSeq);

                log.info("群聊消息拉取补偿批次完成 - 用户ID: " + userId + ", 会话ID: " + conversationId +
                        ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 拉取数量: " + totalPulled);
            }

            log.info("群聊消息拉取补偿完成 - 用户ID: " + userId + ", 会话ID: " + conversationId + ", 总拉取数量: " + totalPulled);

            if (callback != null) {
                // 返回拉取到的消息列表
                List<ChatMessage> pulledMessages = result.getMessages() != null ? result.getMessages() : new ArrayList<>();
                callback.onSuccess(totalPulled, pulledMessages);
            }

        } catch (Exception e) {
            log.severe("群聊消息拉取补偿异常 - 用户ID: " + userId + ", 会话ID: " + conversationId + ", 异常: " + e.getMessage());
            if (callback != null) {
                callback.onFailure("拉取补偿异常: " + e.getMessage());
            }
        } finally {
            isPulling.set(false);
        }
    }
    
    /**
     * 批量拉取私聊消息
     */
    private PullResult pullPrivateMessagesBatch(long fromSeq, long toSeq) {
        for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
            try {
                // 调用HTTP API拉取消息
                HttpClient.PullMessagesResponse response = httpClient.pullMessagesBatch(userId, fromSeq, (int)(toSeq - fromSeq + 1));

                if (response != null && response.isSuccess()) {
                    // 转换Object列表为ChatMessage列表
                    List<ChatMessage> chatMessages = convertObjectListToChatMessages(response.getMessages());
                    return new PullResult(true, chatMessages, null);
                } else {
                    String error = response != null ? response.getErrorMessage() : "网络错误";
                    log.warning("私聊消息拉取失败 (重试 " + (retry + 1) + "/" + MAX_RETRY_COUNT + ") - 用户ID: " + userId +
                            ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 错误: " + error);

                    if (retry < MAX_RETRY_COUNT - 1) {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)); // 指数退避
                    } else {
                        return new PullResult(false, null, error);
                    }
                }

            } catch (Exception e) {
                log.warning("私聊消息拉取异常 (重试 " + (retry + 1) + "/" + MAX_RETRY_COUNT + ") - 用户ID: " + userId +
                        ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 异常: " + e.getMessage());
                
                if (retry < MAX_RETRY_COUNT - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new PullResult(false, null, "拉取被中断");
                    }
                } else {
                    return new PullResult(false, null, "拉取异常: " + e.getMessage());
                }
            }
        }
        
        return new PullResult(false, null, "拉取失败，已达最大重试次数");
    }

    /**
     * 批量拉取群聊消息
     */
    private PullResult pullGroupMessagesBatch(String conversationId, long fromSeq, long toSeq) {
        for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
            try {
                // 构建群聊消息拉取请求
                Map<String, Long> conversationSeqMap = new HashMap<>();
                conversationSeqMap.put(conversationId, fromSeq - 1); // 从fromSeq-1开始拉取，确保包含fromSeq

                // 调用HTTP API拉取群聊消息
                HttpClient.PullGroupMessagesResponse response = httpClient.pullGroupMessages(userId, conversationSeqMap, (int)(toSeq - fromSeq + 1));

                if (response != null && response.isSuccess()) {
                    // 转换Object列表为ChatMessage列表
                    List<ChatMessage> chatMessages = convertObjectListToChatMessages(response.getMessages(httpClient));
                    return new PullResult(true, chatMessages, null);
                } else {
                    String error = response != null ? response.getErrorMessage() : "网络错误";
                    log.warning("群聊消息拉取失败 (重试 " + (retry + 1) + "/" + MAX_RETRY_COUNT + ") - 用户ID: " + userId +
                            ", 会话ID: " + conversationId + ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 错误: " + error);

                    if (retry < MAX_RETRY_COUNT - 1) {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)); // 指数退避
                    } else {
                        return new PullResult(false, null, error);
                    }
                }

            } catch (Exception e) {
                log.warning("群聊消息拉取异常 (重试 " + (retry + 1) + "/" + MAX_RETRY_COUNT + ") - 用户ID: " + userId +
                        ", 会话ID: " + conversationId + ", 序列号范围: [" + fromSeq + ", " + toSeq + "], 异常: " + e.getMessage());

                if (retry < MAX_RETRY_COUNT - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new PullResult(false, null, "拉取被中断");
                    }
                } else {
                    return new PullResult(false, null, "拉取异常: " + e.getMessage());
                }
            }
        }

        return new PullResult(false, null, "群聊消息拉取失败，已达最大重试次数");
    }

    /**
     * 将Object列表转换为ChatMessage列表
     *
     * @param objectList HttpClient返回的Object列表
     * @return ChatMessage列表
     */
    private List<ChatMessage> convertObjectListToChatMessages(List<Object> objectList) {
        List<ChatMessage> chatMessages = new ArrayList<>();

        if (objectList == null || objectList.isEmpty()) {
            return chatMessages;
        }

        for (Object obj : objectList) {
            try {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) obj;

                    ChatMessage chatMessage = convertMapToChatMessage(messageMap);
                    if (chatMessage != null) {
                        chatMessages.add(chatMessage);
                    }
                }
            } catch (Exception e) {
                log.warning("转换消息失败: " + e.getMessage() + ", 消息对象: " + obj);
            }
        }

        log.info("消息转换完成 - 原始数量: " + objectList.size() + ", 转换后数量: " + chatMessages.size());
        return chatMessages;
    }

    /**
     * 将Map转换为ChatMessage
     *
     * @param messageMap 消息Map
     * @return ChatMessage对象
     */
    private ChatMessage convertMapToChatMessage(Map<String, Object> messageMap) {
        try {
            ChatMessage.Builder builder = ChatMessage.newBuilder();

            // 设置基本字段
            builder.setUid(safeGetString(messageMap, "msgId", ""));
            builder.setFromId(safeGetString(messageMap, "fromUserId", ""));
            builder.setToId(safeGetString(messageMap, "toUserId", ""));
            builder.setContent(safeGetString(messageMap, "content", ""));
            builder.setConversationId(safeGetString(messageMap, "conversationId", ""));
            builder.setSeq(safeGetString(messageMap, "seq", ""));

            // 设置消息类型 - 应用SRP原则，提取专门的类型转换方法
            builder.setType(convertMessageType(messageMap.get("msgType")));

            // 设置时间戳 - 应用OCP原则，支持多种时间格式
            builder.setTimestamp(convertTimestamp(messageMap.get("createTime")));

            // 设置推拉结合模式的序列号字段（从服务端返回的数据中提取）
            // 注意：这里需要根据实际的服务端返回格式来设置
            builder.setUserSeq(0L); // 暂时设置为0，实际需要从服务端获取
            builder.setConversationSeq(0L); // 暂时设置为0，实际需要从服务端获取
            builder.setExpectedSeq(0L); // 客户端不需要设置期望序列号

            return builder.build();

        } catch (Exception e) {
            log.warning("转换单个消息失败: " + e.getMessage() + ", 消息Map: " + messageMap);
            return null;
        }
    }

    /**
     * 安全获取字符串值 - DRY原则，复用字符串获取逻辑
     *
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 转换消息类型 - SRP原则，专门处理消息类型转换
     *
     * @param msgTypeObj 消息类型对象
     * @return ChatMessage类型常量
     */
    private int convertMessageType(Object msgTypeObj) {
        if (msgTypeObj == null) {
            return 0; // 默认类型
        }

        try {
            int type = safeParseInt(msgTypeObj, 0);
            // 根据MessageInfo的msgType转换为ChatMessage的type
            switch (type) {
                case 1:
                    return MessageTypeConstants.PRIVATE_CHAT_MESSAGE;
                case 2:
                    return MessageTypeConstants.GROUP_CHAT_MESSAGE;
                default:
                    return 0; // 默认类型
            }
        } catch (Exception e) {
            log.warning("消息类型转换失败: " + msgTypeObj + ", 使用默认类型");
            return 0;
        }
    }

    /**
     * 转换时间戳 - OCP原则，支持多种时间格式的扩展
     *
     * @param createTimeObj 创建时间对象
     * @return 时间戳（毫秒）
     */
    private long convertTimestamp(Object createTimeObj) {
        if (createTimeObj == null) {
            return System.currentTimeMillis();
        }

        String timeStr = createTimeObj.toString();
        
        try {
            // 尝试直接解析为数字时间戳
            return Long.parseLong(timeStr);
        } catch (NumberFormatException e1) {
            try {
                // 尝试解析ISO时间格式 (如: 2025-08-31T08:42:04.000+00:00)
                return parseISODateTime(timeStr);
            } catch (Exception e2) {
                log.warning("时间戳转换失败 - 原始值: " + timeStr + 
                          ", 数字解析错误: " + e1.getMessage() + 
                          ", ISO解析错误: " + e2.getMessage() + 
                          ", 使用当前时间");
                return System.currentTimeMillis();
            }
        }
    }

    /**
     * 解析ISO日期时间格式 - KISS原则，保持简单实用
     *
     * @param isoDateTime ISO格式的日期时间字符串
     * @return 时间戳（毫秒）
     */
    private long parseISODateTime(String isoDateTime) {
        try {
            // 使用Java 8的时间API解析ISO格式
            java.time.Instant instant = java.time.Instant.parse(isoDateTime);
            return instant.toEpochMilli();
        } catch (Exception e) {
            // 如果解析失败，尝试简化版本（去掉时区）
            try {
                String simplifiedTime = isoDateTime.replaceAll("[+].*$", "Z");
                java.time.Instant instant = java.time.Instant.parse(simplifiedTime);
                return instant.toEpochMilli();
            } catch (Exception e2) {
                throw new RuntimeException("无法解析时间格式: " + isoDateTime, e2);
            }
        }
    }

    /**
     * 安全解析整数 - DRY原则，复用数字解析逻辑
     *
     * @param obj 对象
     * @param defaultValue 默认值
     * @return 整数值
     */
    private int safeParseInt(Object obj, int defaultValue) {
        if (obj == null) {
            return defaultValue;
        }

        try {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            } else {
                return Integer.parseInt(obj.toString());
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 关闭拉取补偿管理器
     */
    public void shutdown() {
        log.info("关闭拉取补偿管理器 - 用户ID: " + userId);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 拉取结果
     */
    private static class PullResult {
        private final boolean success;
        private final List<ChatMessage> messages;
        private final String errorMessage;
        
        public PullResult(boolean success, List<ChatMessage> messages, String errorMessage) {
            this.success = success;
            this.messages = messages;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public List<ChatMessage> getMessages() { return messages; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 补偿完成回调接口
     */
    public interface CompensationCallback {
        /**
         * 补偿成功
         * @param pulledCount 拉取到的消息数量
         * @param pulledMessages 拉取到的消息列表
         */
        void onSuccess(int pulledCount, List<ChatMessage> pulledMessages);

        /**
         * 补偿失败
         * @param errorMessage 错误信息
         */
        void onFailure(String errorMessage);
    }
}
