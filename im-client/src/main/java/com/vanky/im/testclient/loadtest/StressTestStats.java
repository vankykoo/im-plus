package com.vanky.im.testclient.loadtest;

/**
 * 压测统计数据类
 * 用于封装压测过程中的性能指标
 * 
 * 遵循不可变对象设计，确保数据一致性
 * 
 * @author vanky
 * @since 2025-08-29
 */
public class StressTestStats {
    
    private final int totalSent;           // 已发送总数
    private final int successCount;        // 成功数量
    private final int failureCount;        // 失败数量
    private final double successRate;      // 成功率 (%)
    private final double avgResponseTime;  // 平均响应时间 (ms)
    private final long minResponseTime;    // 最小响应时间 (ms)
    private final long maxResponseTime;    // 最大响应时间 (ms)
    private final double tps;              // 每秒事务数 (Transaction Per Second)
    private final long elapsedTime;        // 已耗时 (ms)
    
    public StressTestStats(int totalSent, int successCount, int failureCount, 
                          double successRate, double avgResponseTime, 
                          long minResponseTime, long maxResponseTime, 
                          double tps, long elapsedTime) {
        this.totalSent = totalSent;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successRate = successRate;
        this.avgResponseTime = avgResponseTime;
        this.minResponseTime = minResponseTime;
        this.maxResponseTime = maxResponseTime;
        this.tps = tps;
        this.elapsedTime = elapsedTime;
    }
    
    // Getters
    public int getTotalSent() { return totalSent; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
    public double getSuccessRate() { return successRate; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public long getMinResponseTime() { return minResponseTime; }
    public long getMaxResponseTime() { return maxResponseTime; }
    public double getTps() { return tps; }
    public long getElapsedTime() { return elapsedTime; }
    
    /**
     * 格式化为可读字符串
     */
    public String toDisplayString() {
        return String.format(
            "已发送: %d | 成功: %d | 失败: %d | 成功率: %.1f%% | " +
            "平均响应: %.1fms | 最小: %dms | 最大: %dms | TPS: %.1f | 耗时: %ds",
            totalSent, successCount, failureCount, successRate,
            avgResponseTime, minResponseTime, maxResponseTime, 
            tps, elapsedTime / 1000
        );
    }
    
    /**
     * 格式化为详细报告
     */
    public String toDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 压测统计报告 ===\n");
        sb.append(String.format("消息发送总数: %d\n", totalSent));
        sb.append(String.format("成功发送数量: %d\n", successCount));
        sb.append(String.format("失败发送数量: %d\n", failureCount));
        sb.append(String.format("成功率: %.2f%%\n", successRate));
        sb.append(String.format("平均响应时间: %.2f ms\n", avgResponseTime));
        sb.append(String.format("最小响应时间: %d ms\n", minResponseTime));
        sb.append(String.format("最大响应时间: %d ms\n", maxResponseTime));
        sb.append(String.format("吞吐量(TPS): %.2f\n", tps));
        sb.append(String.format("总耗时: %.2f 秒\n", elapsedTime / 1000.0));
        sb.append("==================\n");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return toDisplayString();
    }
}
