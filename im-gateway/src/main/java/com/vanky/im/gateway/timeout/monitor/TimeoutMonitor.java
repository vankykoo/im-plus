package com.vanky.im.gateway.timeout.monitor;

import com.vanky.im.gateway.timeout.TimeoutManager;
import com.vanky.im.gateway.timeout.model.TimeoutStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时重发监控器
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 定期监控超时重发机制的运行状态
 */
@Slf4j
@Component
public class TimeoutMonitor {
    
    @Autowired
    private TimeoutManager timeoutManager;
    
    /**
     * 定期打印统计信息（每5分钟）
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void printStats() {
        if (!timeoutManager.isEnabled()) {
            return;
        }
        
        try {
            TimeoutStats stats = timeoutManager.getStats();
            
            log.info("超时重发统计 - {}", stats.toString());
            
            // 如果成功率过低，记录警告
            if (stats.getSuccessRate() < 80.0 && stats.getTotalTasksAdded() > 100) {
                log.warn("消息确认成功率过低: {:.2f}%, 请检查客户端连接状态", stats.getSuccessRate());
            }
            
            // 如果平均重试次数过高，记录警告
            if (stats.getAverageRetries() > 1.5 && stats.getTotalTasksAdded() > 100) {
                log.warn("平均重试次数过高: {:.2f}, 请检查网络状况", stats.getAverageRetries());
            }
            
            // 如果待处理任务过多，记录警告
            if (stats.getCurrentPendingTasks() > 1000) {
                log.warn("待处理超时任务过多: {}, 请检查系统负载", stats.getCurrentPendingTasks());
            }
            
        } catch (Exception e) {
            log.error("获取超时重发统计信息失败", e);
        }
    }
    
    /**
     * 获取当前统计信息
     * 
     * @return 统计信息
     */
    public TimeoutStats getCurrentStats() {
        if (!timeoutManager.isEnabled()) {
            return new TimeoutStats();
        }
        return timeoutManager.getStats();
    }
    
    /**
     * 检查系统健康状态
     * 
     * @return true if healthy
     */
    public boolean isHealthy() {
        if (!timeoutManager.isEnabled()) {
            return true;
        }
        
        try {
            TimeoutStats stats = timeoutManager.getStats();
            
            // 健康检查条件
            boolean successRateOk = stats.getSuccessRate() >= 70.0 || stats.getTotalTasksAdded() < 50;
            boolean averageRetriesOk = stats.getAverageRetries() <= 2.0 || stats.getTotalTasksAdded() < 50;
            boolean pendingTasksOk = stats.getCurrentPendingTasks() <= 2000;
            
            return successRateOk && averageRetriesOk && pendingTasksOk;
            
        } catch (Exception e) {
            log.error("检查超时重发健康状态失败", e);
            return false;
        }
    }
    
    /**
     * 获取健康检查报告
     * 
     * @return 健康检查报告
     */
    public String getHealthReport() {
        if (!timeoutManager.isEnabled()) {
            return "超时重发机制已禁用";
        }
        
        try {
            TimeoutStats stats = timeoutManager.getStats();
            StringBuilder report = new StringBuilder();
            
            report.append("超时重发健康报告:\n");
            report.append(String.format("- 总任务数: %d\n", stats.getTotalTasksAdded()));
            report.append(String.format("- 成功确认数: %d\n", stats.getTotalTasksCancelled()));
            report.append(String.format("- 超时任务数: %d\n", stats.getTotalTasksTimeout()));
            report.append(String.format("- 总重试次数: %d\n", stats.getTotalRetries()));
            report.append(String.format("- 放弃任务数: %d\n", stats.getTotalTasksAbandoned()));
            report.append(String.format("- 当前待处理: %d\n", stats.getCurrentPendingTasks()));
            report.append(String.format("- 成功率: %.2f%%\n", stats.getSuccessRate()));
            report.append(String.format("- 平均重试: %.2f次\n", stats.getAverageRetries()));
            report.append(String.format("- 运行时长: %d秒\n", stats.getUptimeSeconds()));
            report.append(String.format("- 健康状态: %s", isHealthy() ? "健康" : "异常"));
            
            return report.toString();
            
        } catch (Exception e) {
            return "获取健康报告失败: " + e.getMessage();
        }
    }
}
