package com.vanky.im.sequence.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 消息服务客户端
 * 负责调用 im-message-server 服务查询最大序列号
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Slf4j
@Component
public class MessageClient {

    @Value("${sequence.recovery.message-service-url:http://localhost:8081}")
    private String messageServiceUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MessageClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 根据业务key查询最大序列号
     * 
     * @param businessKey 业务key，如 "user_12345" 或 "conversation_67890"
     * @return 最大序列号，失败时返回0
     */
    public Long getMaxSeqByBusinessKey(String businessKey) {
        try {
            if (businessKey == null || businessKey.trim().isEmpty()) {
                log.warn("业务key为空，返回默认值0");
                return 0L;
            }

            String url = buildQueryUrl(businessKey);
            if (url == null) {
                log.warn("无法识别的业务key格式: {}, 返回默认值0", businessKey);
                return 0L;
            }

            // 构建HTTP请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                
                Boolean success = (Boolean) responseMap.get("success");
                if (Boolean.TRUE.equals(success)) {
                    Object maxSeqObj = responseMap.get("maxSeq");
                    Long maxSeq = maxSeqObj instanceof Integer ? ((Integer) maxSeqObj).longValue() : (Long) maxSeqObj;
                    
                    log.debug("查询最大序列号成功 - 业务key: {}, 最大序列号: {}", businessKey, maxSeq);
                    return maxSeq != null ? maxSeq : 0L;
                } else {
                    String errorMessage = (String) responseMap.get("errorMessage");
                    log.error("消息服务返回错误 - 业务key: {}, 错误: {}", businessKey, errorMessage);
                    return 0L;
                }
            } else {
                log.error("消息服务返回HTTP {} - 业务key: {}, 响应: {}", response.statusCode(), businessKey, response.body());
                return 0L;
            }

        } catch (Exception e) {
            log.error("查询最大序列号失败 - 业务key: {}", businessKey, e);
            return 0L;
        }
    }

    /**
     * 根据业务key构建查询URL
     * 
     * @param businessKey 业务key
     * @return 查询URL，无法识别时返回null
     */
    private String buildQueryUrl(String businessKey) {
        if (businessKey.startsWith("user_")) {
            String userId = businessKey.substring(5); // 去掉 "user_" 前缀
            return messageServiceUrl + "/api/message/max-seq/user/" + userId;
        } else if (businessKey.startsWith("conversation_")) {
            String conversationId = businessKey.substring(13); // 去掉 "conversation_" 前缀
            return messageServiceUrl + "/api/message/max-seq/conversation/" + conversationId;
        } else {
            return null;
        }
    }

    /**
     * 健康检查
     * 
     * @return 是否健康
     */
    public boolean isHealthy() {
        try {
            String url = messageServiceUrl + "/api/message/max-seq/health";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                return "UP".equals(responseMap.get("status"));
            }
            
            return false;
        } catch (Exception e) {
            log.warn("消息服务健康检查失败", e);
            return false;
        }
    }
}
