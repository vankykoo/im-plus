package com.vanky.im.message.client;

import com.vanky.im.message.model.dto.SequenceRequest;
import com.vanky.im.message.model.dto.SequenceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Autowired
    private SequenceServiceFeignClient sequenceServiceFeignClient;

    /**
     * 获取单个序列号
     *
     * @param businessKey 业务key，如 "user_12345" 或 "group_67890"
     * @return 序列号，失败时返回null
     */
    public Long getNextSequence(String businessKey) {
        try {
            SequenceRequest.Single request = new SequenceRequest.Single(businessKey);
            ResponseEntity<SequenceResponse.Single> responseEntity = sequenceServiceFeignClient.getNextSequence(request);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                SequenceResponse.Single response = responseEntity.getBody();
                if (response.getSuccess()) {
                    log.debug("Generated sequence for key: {}, seq: {}", businessKey, response.getSeq());
                    return response.getSeq();
                } else {
                    log.error("Sequence service returned error for key: {}, error: {}",
                            businessKey, response.getErrorMessage());
                    return null;
                }
            } else {
                log.error("Sequence service returned HTTP {}: {}", responseEntity.getStatusCode(), responseEntity.getBody());
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
     * @param count        每个key需要的序列号数量
     * @return key -> 起始序列号的映射，失败的key不包含在结果中
     */
    public Map<String, Long> getBatchSequences(List<String> businessKeys, int count) {
        try {
            Map<String, Integer> keysMap = businessKeys.stream()
                    .collect(Collectors.toMap(key -> key, key -> count));
            SequenceRequest.Batch request = new SequenceRequest.Batch(keysMap);
            ResponseEntity<SequenceResponse.Batch> responseEntity = sequenceServiceFeignClient.getBatchSequences(request);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                SequenceResponse.Batch response = responseEntity.getBody();
                if (response.getSuccess() && response.getResults() != null) {
                    Map<String, Long> result = new HashMap<>();
                    for (Map.Entry<String, SequenceResponse.SequenceResult> entry : response.getResults().entrySet()) {
                        SequenceResponse.SequenceResult sequenceResult = entry.getValue();
                        if (sequenceResult.getSuccess()) {
                            result.put(entry.getKey(), sequenceResult.getStartSeq());
                        } else {
                            log.error("Failed to get sequence for key: {}, error: {}",
                                    entry.getKey(), sequenceResult.getErrorMessage());
                        }
                    }
                    log.debug("Generated batch sequences for {} keys, success: {}",
                            businessKeys.size(), result.size());
                    return result;
                } else {
                    log.error("Batch sequence service returned error: {}", response.getErrorMessage());
                }
            } else {
                log.error("Batch sequence service returned HTTP {}: {}", responseEntity.getStatusCode(), responseEntity.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to get batch sequences for keys: {}", businessKeys, e);
        }
        return Collections.emptyMap();
    }

    /**
     * 检查序列号服务健康状态
     *
     * @return 是否健康
     */
    public boolean isHealthy() {
        // Feign自带熔断和重试机制，这里可以简化健康检查
        // 实际生产中可以调用一个专门的health接口
        try {
            // 尝试获取一个测试序列号
            Long seq = getNextSequence("health_check");
            return seq != null;
        } catch (Exception e) {
            log.warn("Sequence service health check failed", e);
            return false;
        }
    }
}
