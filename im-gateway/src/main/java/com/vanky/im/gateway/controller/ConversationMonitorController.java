package com.vanky.im.gateway.controller;

import com.vanky.im.gateway.conversation.ConversationWorkerPool;
import com.vanky.im.gateway.conversation.ConversationProcessorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话级串行化处理监控接口
 * 
 * @author vanky
 * @create 2025-08-31
 * @description 提供会话级串行化处理的监控接口
 */
@RestController
@RequestMapping("/api/conversation")
public class ConversationMonitorController {
    
    @Autowired
    private ConversationWorkerPool workerPool;
    
    @Autowired
    private ConversationProcessorConfig config;
    
    /**
     * 获取会话处理器统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
            result.put("enabled", config.isEnabled());
            result.put("statistics", workerPool.getStatistics());
            result.put("workerHealth", workerPool.getWorkerHealthStatus());
            result.put("allWorkersHealthy", workerPool.areAllWorkersHealthy());
            result.put("config", config.getSummary());
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
            boolean enabled = config.isEnabled();
            boolean healthy = !enabled || workerPool.areAllWorkersHealthy();
            
            result.put("success", true);
            result.put("enabled", enabled);
            result.put("healthy", healthy);
            
            if (enabled) {
                result.put("workerHealth", workerPool.getWorkerHealthStatus());
            } else {
                result.put("message", "会话级串行化处理已禁用");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("healthy", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取配置信息
     * 
     * @return 配置信息
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("success", true);
            result.put("config", config);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
