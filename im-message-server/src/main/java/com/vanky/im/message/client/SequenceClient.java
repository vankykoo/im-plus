package com.vanky.im.message.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 序列号服务客户端
 * 负责调用 im-sequence 服务获取序列号
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@Component
public class SequenceClient {

    @Value("${sequence.service.url:http://localhost:8084}")
    private String sequenceServiceUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SequenceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取单个序列号
     * 
     * @param businessKey 业务key，如 "user_12345" 或 "group_67890"
     * @return 序列号，失败时返回null
     */
    public Long getNextSequence(String businessKey) {
        try {
            // 构建请求体
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("key", businessKey);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 构建HTTP请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sequenceServiceUrl + "/api/sequence/next"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                SequenceResponse sequenceResponse = objectMapper.readValue(response.body(), SequenceResponse.class);
                if (sequenceResponse.getSuccess()) {
                    log.debug("Generated sequence for key: {}, seq: {}", businessKey, sequenceResponse.getSeq());
                    return sequenceResponse.getSeq();
                } else {
                    log.error("Sequence service returned error for key: {}, error: {}", 
                             businessKey, sequenceResponse.getErrorMessage());
                    return null;
                }
            } else {
                log.error("Sequence service returned HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to get sequence for key: {}", businessKey, e);
            return null;
        }
    }

    /**
     * 批量获取序列号
     * 
     * @param businessKeys 业务key列表
     * @param count 每个key需要的序列号数量
     * @return key -> 起始序列号的映射，失败的key不包含在结果中
     */
    public Map<String, Long> getBatchSequences(List<String> businessKeys, int count) {
        Map<String, Long> result = new HashMap<>();
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("keys", businessKeys);
            requestBody.put("count", count);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 构建HTTP请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sequenceServiceUrl + "/api/sequence/next-batch"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                BatchSequenceResponse batchResponse = objectMapper.readValue(response.body(), BatchSequenceResponse.class);
                if (batchResponse.getSuccess() && batchResponse.getResults() != null) {
                    for (Map.Entry<String, SequenceResult> entry : batchResponse.getResults().entrySet()) {
                        SequenceResult sequenceResult = entry.getValue();
                        if (sequenceResult.getSuccess()) {
                            result.put(entry.getKey(), sequenceResult.getStartSeq());
                        } else {
                            log.error("Failed to get sequence for key: {}, error: {}", 
                                     entry.getKey(), sequenceResult.getErrorMessage());
                        }
                    }
                    log.debug("Generated batch sequences for {} keys, success: {}", 
                             businessKeys.size(), result.size());
                } else {
                    log.error("Batch sequence service returned error: {}", batchResponse.getErrorMessage());
                }
            } else {
                log.error("Batch sequence service returned HTTP {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("Failed to get batch sequences for keys: {}", businessKeys, e);
        }
        
        return result;
    }

    /**
     * 检查序列号服务健康状态
     * 
     * @return 是否健康
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sequenceServiceUrl + "/api/sequence/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            log.warn("Sequence service health check failed", e);
            return false;
        }
    }

    /**
     * 序列号响应DTO
     */
    @Data
    private static class SequenceResponse {
        private Long seq;
        private Boolean success;
        private String errorMessage;
    }

    /**
     * 批量序列号响应DTO
     */
    @Data
    private static class BatchSequenceResponse {
        private Map<String, SequenceResult> results;
        private Boolean success;
        private String errorMessage;
    }

    /**
     * 序列号结果DTO
     */
    @Data
    private static class SequenceResult {
        private Long startSeq;
        private Integer count;
        private Boolean success;
        private String errorMessage;
    }
}
