package com.vanky.im.message.controller;

import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.common.monitor.RedisKeyMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Redis监控控制器
 * 提供Redis性能监控和管理的HTTP接口
 * 
 * 设计原则：
 * - KISS: 简单的REST API设计
 * - SOLID-S: 单一职责，只负责监控接口
 * - YAGNI: 只提供必要的监控接口
 * 
 * @author vanky
 * @since 2025-08-31
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/redis")
public class RedisMonitorController {

    @Autowired
    private RedisKeyMonitor redisKeyMonitor;

    /**
     * 获取Redis性能指标
     * 
     * @return 性能指标信息
     */
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = redisKeyMonitor.getPerformanceMetrics();
            log.debug("获取Redis性能指标成功 - 指标数量: {}", metrics.size());
            return ApiResponse.success(metrics);
            
        } catch (Exception e) {
            log.error("获取Redis性能指标失败", e);
            return ApiResponse.error("获取性能指标失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发大KEY清理
     * 
     * @return 清理结果
     */
    @PostMapping("/cleanup")
    public ApiResponse<String> cleanupLargeKeys() {
        try {
            redisKeyMonitor.cleanupLargeKeys();
            log.info("手动触发Redis大KEY清理完成");
            return ApiResponse.success("大KEY清理完成");
            
        } catch (Exception e) {
            log.error("手动清理Redis大KEY失败", e);
            return ApiResponse.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 重置监控指标
     * 
     * @return 重置结果
     */
    @PostMapping("/reset-metrics")
    public ApiResponse<String> resetMetrics() {
        try {
            redisKeyMonitor.resetMetrics();
            log.info("Redis监控指标重置完成");
            return ApiResponse.success("监控指标已重置");
            
        } catch (Exception e) {
            log.error("重置Redis监控指标失败", e);
            return ApiResponse.error("重置失败: " + e.getMessage());
        }
    }

    /**
     * 获取Redis健康状态
     * 
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> getHealth() {
        try {
            Map<String, Object> metrics = redisKeyMonitor.getPerformanceMetrics();
            
            // 简单的健康状态判断
            boolean healthy = true;
            String status = "HEALTHY";
            String message = "Redis运行正常";
            
            Long largeKeyWarnings = (Long) metrics.get("largeKeyWarnings");
            Long hotKeyWarnings = (Long) metrics.get("hotKeyWarnings");
            
            if (largeKeyWarnings != null && largeKeyWarnings > 10) {
                healthy = false;
                status = "WARNING";
                message = "检测到较多大KEY警告";
            }
            
            if (hotKeyWarnings != null && hotKeyWarnings > 5) {
                healthy = false;
                status = "WARNING";
                message = "检测到较多热KEY警告";
            }
            
            Map<String, Object> health = Map.of(
                "status", status,
                "healthy", healthy,
                "message", message,
                "timestamp", System.currentTimeMillis(),
                "metrics", metrics
            );
            
            log.debug("获取Redis健康状态 - 状态: {}", status);
            return ApiResponse.success(health);
            
        } catch (Exception e) {
            log.error("获取Redis健康状态失败", e);
            return ApiResponse.error("获取健康状态失败: " + e.getMessage());
        }
    }
}
