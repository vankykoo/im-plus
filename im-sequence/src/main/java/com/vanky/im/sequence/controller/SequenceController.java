package com.vanky.im.sequence.controller;

import com.vanky.im.sequence.dto.SequenceRequest;
import com.vanky.im.sequence.dto.SequenceResponse;
import com.vanky.im.sequence.service.SequenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 序列号服务控制器
 * 提供序列号生成的REST API接口
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@RestController
@RequestMapping("/api/sequence")
public class SequenceController {

    @Autowired
    private SequenceService sequenceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DataSource dataSource;

    /**
     * 获取单个序列号
     * 
     * @param request 请求参数
     * @return 序列号响应
     */
    @PostMapping("/next")
    public ResponseEntity<SequenceResponse.Single> getNextSequence(@RequestBody SequenceRequest.Single request) {
        log.debug("Received single sequence request for key: {}", request.getKey());
        
        SequenceResponse.Single response = sequenceService.getNextSequence(request);
        
        if (response.getSuccess()) {
            log.debug("Generated sequence for key: {}, seq: {}", request.getKey(), response.getSeq());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to generate sequence for key: {}, error: {}", request.getKey(), response.getErrorMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量获取序列号
     * 
     * @param request 批量请求参数
     * @return 批量序列号响应
     */
    @PostMapping("/next-batch")
    public ResponseEntity<SequenceResponse.Batch> getBatchSequences(@RequestBody SequenceRequest.Batch request) {
        log.debug("Received batch sequence request for {} keys, count: {}", 
                 request.getKeys().size(), request.getCount());
        
        SequenceResponse.Batch response = sequenceService.getBatchSequences(request);
        
        if (response.getSuccess()) {
            log.debug("Generated batch sequences for {} keys", request.getKeys().size());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to generate batch sequences, error: {}", response.getErrorMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 健康检查
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<SequenceResponse.Health> health() {
        SequenceResponse.Health health = new SequenceResponse.Health();
        Map<String, Object> details = new HashMap<>();
        
        try {
            // 检查Redis连接
            String redisStatus = "UP";
            try {
                redisTemplate.opsForValue().get("health_check");
                details.put("redis", "Connected");
            } catch (Exception e) {
                redisStatus = "DOWN";
                details.put("redis", "Connection failed: " + e.getMessage());
            }
            
            // 检查数据库连接
            String dbStatus = "UP";
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    details.put("database", "Connected");
                } else {
                    dbStatus = "DOWN";
                    details.put("database", "Connection invalid");
                }
            } catch (Exception e) {
                dbStatus = "DOWN";
                details.put("database", "Connection failed: " + e.getMessage());
            }
            
            // 整体状态
            String overallStatus = "UP".equals(redisStatus) && "UP".equals(dbStatus) ? "UP" : "DOWN";
            
            health.setStatus(overallStatus);
            health.setRedis(redisStatus);
            health.setDatabase(dbStatus);
            health.setDetails(details);
            
            if ("UP".equals(overallStatus)) {
                return ResponseEntity.ok(health);
            } else {
                return ResponseEntity.status(503).body(health);
            }
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            health.setStatus("DOWN");
            health.setRedis("UNKNOWN");
            health.setDatabase("UNKNOWN");
            details.put("error", e.getMessage());
            health.setDetails(details);
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<SequenceResponse.Stats> getStats() {
        try {
            SequenceResponse.Stats stats = sequenceService.getStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            SequenceResponse.Stats errorStats = new SequenceResponse.Stats();
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            errorStats.setDetails(details);
            return ResponseEntity.status(500).body(errorStats);
        }
    }

    /**
     * 简单的ping接口
     * 
     * @return pong
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "pong");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("service", "im-sequence");
        return ResponseEntity.ok(response);
    }
}
