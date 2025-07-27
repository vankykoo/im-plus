package com.vanky.im.gateway.controller;

import com.vanky.im.gateway.timeout.monitor.TimeoutMonitor;
import com.vanky.im.gateway.timeout.model.TimeoutStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 超时重发监控接口
 * 
 * @author vanky
 * @create 2025/7/27
 * @description 提供超时重发机制的监控接口
 */
@RestController
@RequestMapping("/api/timeout")
public class TimeoutMonitorController {
    
    @Autowired
    private TimeoutMonitor timeoutMonitor;
    
    /**
     * 获取超时重发统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            TimeoutStats stats = timeoutMonitor.getCurrentStats();
            result.put("success", true);
            result.put("data", stats);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取健康检查状态
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean healthy = timeoutMonitor.isHealthy();
            String report = timeoutMonitor.getHealthReport();
            
            result.put("success", true);
            result.put("healthy", healthy);
            result.put("report", report);
        } catch (Exception e) {
            result.put("success", false);
            result.put("healthy", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
