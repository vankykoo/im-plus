package com.vanky.im.testclient.loadtest;

import com.vanky.im.testclient.client.IMClient;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压测管理器
 * 负责批量发送消息、统计性能指标和管理压测生命周期
 * 
 * 设计原则：
 * - SRP: 单一职责，专门负责压测逻辑
 * - OCP: 开放扩展，可以轻松添加新的压测类型
 * - DRY: 复用现有的IMClient发送机制
 * 
 * @author vanky
 * @since 2025-08-29
 */
public class StressTestManager {

    private final IMClient imClient;
    private final StressTestCallback callback;
    
    // 压测状态管理
    private volatile boolean isRunning = false;
    private ExecutorService executor;
    private ScheduledExecutorService statsReporter;
    
    // 统计指标
    private final AtomicInteger totalSent = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxResponseTime = new AtomicLong(0);
    
    // 开始时间
    private long startTime;

    public StressTestManager(IMClient imClient, StressTestCallback callback) {
        this.imClient = imClient;
        this.callback = callback;
    }

    /**
     * 开始压测
     * @param config 压测配置
     */
    public synchronized void startStressTest(StressTestConfig config) {
        if (isRunning) {
            callback.onError("压测已在运行中");
            return;
        }
        
        if (!imClient.isConnected() || !imClient.isLoggedIn()) {
            callback.onError("客户端未连接或未登录，无法开始压测");
            return;
        }
        
        // 重置统计数据
        resetStats();
        
        isRunning = true;
        startTime = System.currentTimeMillis();
        
        // 创建线程池 - 基于配置的并发数
        executor = Executors.newFixedThreadPool(config.getConcurrency());
        
        // 创建统计报告线程
        statsReporter = Executors.newSingleThreadScheduledExecutor();
        statsReporter.scheduleAtFixedRate(this::reportStats, 1, 1, TimeUnit.SECONDS);
        
        callback.onStarted(config);
        
        // 提交压测任务
        for (int i = 0; i < config.getMessageCount(); i++) {
            final int messageIndex = i + 1;
            executor.submit(() -> sendMessage(config, messageIndex));
            
            // 如果设置了发送间隔，则延迟提交下一个任务
            if (config.getSendIntervalMs() > 0 && i < config.getMessageCount() - 1) {
                try {
                    Thread.sleep(config.getSendIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        // 异步等待所有任务完成
        CompletableFuture.runAsync(() -> {
            executor.shutdown();
            try {
                if (executor.awaitTermination(config.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                    onTestCompleted();
                } else {
                    onTestTimeout();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onTestInterrupted();
            }
        });
    }

    /**
     * 停止压测
     */
    public synchronized void stopStressTest() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        
        if (statsReporter != null && !statsReporter.isShutdown()) {
            statsReporter.shutdown();
        }
        
        callback.onStopped();
    }

    /**
     * 发送单条消息
     */
    private void sendMessage(StressTestConfig config, int messageIndex) {
        if (!isRunning) {
            return;
        }
        
        long messageStartTime = System.currentTimeMillis();
        String content = String.format(config.getMessageTemplate(), messageIndex);
        
        try {
            // 根据压测类型发送消息
            switch (config.getTestType()) {
                case PRIVATE_MESSAGE:
                    imClient.sendPrivateMessage(config.getTargetId(), content);
                    break;
                case GROUP_MESSAGE:
                    imClient.sendGroupMessage(config.getTargetId(), content);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的压测类型: " + config.getTestType());
            }
            
            // 记录成功统计
            long responseTime = System.currentTimeMillis() - messageStartTime;
            recordSuccess(responseTime);
            
        } catch (Exception e) {
            // 记录失败统计
            recordFailure(e);
        }
        
        totalSent.incrementAndGet();
    }

    /**
     * 记录成功统计
     */
    private void recordSuccess(long responseTime) {
        successCount.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);
        
        // 更新最小响应时间
        long currentMin = minResponseTime.get();
        while (responseTime < currentMin && !minResponseTime.compareAndSet(currentMin, responseTime)) {
            currentMin = minResponseTime.get();
        }
        
        // 更新最大响应时间
        long currentMax = maxResponseTime.get();
        while (responseTime > currentMax && !maxResponseTime.compareAndSet(currentMax, responseTime)) {
            currentMax = maxResponseTime.get();
        }
    }

    /**
     * 记录失败统计
     */
    private void recordFailure(Exception e) {
        failureCount.incrementAndGet();
        callback.onMessageFailed(e.getMessage());
    }

    /**
     * 重置统计数据
     */
    private void resetStats() {
        totalSent.set(0);
        successCount.set(0);
        failureCount.set(0);
        totalResponseTime.set(0);
        minResponseTime.set(Long.MAX_VALUE);
        maxResponseTime.set(0);
    }

    /**
     * 报告统计信息
     */
    private void reportStats() {
        if (!isRunning) {
            return;
        }
        
        StressTestStats stats = generateCurrentStats();
        callback.onStatsUpdate(stats);
    }

    /**
     * 生成当前统计信息
     */
    private StressTestStats generateCurrentStats() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        
        int sent = totalSent.get();
        int success = successCount.get();
        int failure = failureCount.get();
        long totalRespTime = totalResponseTime.get();
        
        double successRate = sent > 0 ? (double) success / sent * 100 : 0;
        double avgResponseTime = success > 0 ? (double) totalRespTime / success : 0;
        double tps = elapsedTime > 0 ? (double) success / (elapsedTime / 1000.0) : 0;
        
        long minRespTime = minResponseTime.get();
        if (minRespTime == Long.MAX_VALUE) {
            minRespTime = 0;
        }
        
        return new StressTestStats(
            sent, success, failure, successRate, 
            avgResponseTime, minRespTime, maxResponseTime.get(),
            tps, elapsedTime
        );
    }

    /**
     * 压测完成
     */
    private void onTestCompleted() {
        isRunning = false;
        if (statsReporter != null) {
            statsReporter.shutdown();
        }
        
        StressTestStats finalStats = generateCurrentStats();
        callback.onCompleted(finalStats);
    }

    /**
     * 压测超时
     */
    private void onTestTimeout() {
        isRunning = false;
        if (statsReporter != null) {
            statsReporter.shutdown();
        }
        
        StressTestStats finalStats = generateCurrentStats();
        callback.onTimeout(finalStats);
    }

    /**
     * 压测被中断
     */
    private void onTestInterrupted() {
        isRunning = false;
        if (statsReporter != null) {
            statsReporter.shutdown();
        }
        
        callback.onError("压测被中断");
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 压测回调接口
     */
    public interface StressTestCallback {
        void onStarted(StressTestConfig config);
        void onStatsUpdate(StressTestStats stats);
        void onMessageFailed(String error);
        void onCompleted(StressTestStats finalStats);
        void onTimeout(StressTestStats finalStats);
        void onStopped();
        void onError(String error);
    }
}
