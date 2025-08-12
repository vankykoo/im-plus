package com.vanky.im.sequence.service;

import com.vanky.im.sequence.config.SequenceConfig;
import com.vanky.im.sequence.constant.SequenceConstants;
import com.vanky.im.sequence.dto.SequenceRequest;
import com.vanky.im.sequence.dto.SequenceResponse;
import com.vanky.im.sequence.util.SectionIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 序列号生成服务
 * 核心业务逻辑，负责序列号的生成和管理
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@Service
public class SequenceService {

    @Autowired
    private LuaScriptService luaScriptService;

    @Autowired
    private SequencePersistenceService persistenceService;

    @Autowired
    private SequenceConfig sequenceConfig;

    /**
     * 统计信息
     */
    private final AtomicLong totalGenerated = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    /**
     * 获取单个序列号
     * 
     * @param request 请求参数
     * @return 序列号响应
     */
    public SequenceResponse.Single getNextSequence(SequenceRequest.Single request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            String businessKey = request.getKey();
            if (businessKey == null || businessKey.trim().isEmpty()) {
                totalErrors.incrementAndGet();
                return SequenceResponse.Single.failure("Business key cannot be empty");
            }

            // 生成分段键
            String sectionKey = SectionIdGenerator.generateSectionKey(businessKey);

            // 生成Redis Key
            String redisKey = sequenceConfig.getRedis().getKeyPrefix() + sectionKey;
            
            // 执行Lua脚本获取序列号
            List<String> luaResult = luaScriptService.executeGetNextSeq(
                    redisKey, sequenceConfig.getSection().getStepSize());
            
            if (luaResult == null || luaResult.isEmpty()) {
                totalErrors.incrementAndGet();
                return SequenceResponse.Single.failure("Failed to execute Lua script");
            }
            
            String seqStr = luaResult.get(0);
            if ("-1".equals(seqStr)) {
                totalErrors.incrementAndGet();
                String errorMsg = luaResult.size() > 2 ? luaResult.get(2) : "Unknown error";
                return SequenceResponse.Single.failure("Lua script error: " + errorMsg);
            }
            
            Long seq = Long.parseLong(seqStr);
            totalGenerated.incrementAndGet();
            
            // 检查是否需要持久化
            if (luaResult.size() > 1 && SequenceConstants.LuaResult.PERSIST.equals(luaResult.get(1))) {
                Long maxSeq = Long.parseLong(luaResult.get(2));
                // 异步持久化
                if (sequenceConfig.getPersistence().isEnabled()) {
                    persistenceService.persistMaxSeqAsync(sectionKey, maxSeq);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Generated sequence for key: {}, seq: {}, duration: {}ms", businessKey, seq, duration);
            
            return SequenceResponse.Single.success(seq);
            
        } catch (Exception e) {
            totalErrors.incrementAndGet();
            log.error("Failed to generate sequence for key: {}", request.getKey(), e);
            return SequenceResponse.Single.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * 批量获取序列号
     * 
     * @param request 批量请求参数
     * @return 批量序列号响应
     */
    public SequenceResponse.Batch getBatchSequences(SequenceRequest.Batch request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            List<String> keys = request.getKeys();
            Integer count = request.getCount();
            
            if (keys == null || keys.isEmpty()) {
                totalErrors.incrementAndGet();
                return SequenceResponse.Batch.failure("Keys cannot be empty");
            }
            
            if (count == null || count <= 0) {
                count = 1;
            }
            
            Map<String, SequenceResponse.SequenceResult> results = new HashMap<>();
            
            for (String businessKey : keys) {
                try {
                    SequenceResponse.SequenceResult result = generateBatchSequenceForKey(businessKey, count);
                    results.put(businessKey, result);
                    
                    if (result.getSuccess()) {
                        totalGenerated.addAndGet(count);
                    } else {
                        totalErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Failed to generate batch sequence for key: {}", businessKey, e);
                    results.put(businessKey, SequenceResponse.SequenceResult.failure("Error: " + e.getMessage()));
                    totalErrors.incrementAndGet();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Generated batch sequences for {} keys, count: {}, duration: {}ms", 
                     keys.size(), count, duration);
            
            return SequenceResponse.Batch.success(results);
            
        } catch (Exception e) {
            totalErrors.incrementAndGet();
            log.error("Failed to generate batch sequences", e);
            return SequenceResponse.Batch.failure("Internal error: " + e.getMessage());
        }
    }

    /**
     * 为单个key生成批量序列号
     * 
     * @param businessKey 业务key
     * @param count 数量
     * @return 序列号结果
     */
    private SequenceResponse.SequenceResult generateBatchSequenceForKey(String businessKey, Integer count) {
        // 对于批量请求，我们连续调用Lua脚本获取连续的序列号
        Long startSeq = null;
        
        for (int i = 0; i < count; i++) {
            SequenceRequest.Single singleRequest = new SequenceRequest.Single();
            singleRequest.setKey(businessKey);
            
            SequenceResponse.Single singleResponse = getNextSequence(singleRequest);
            
            if (!singleResponse.getSuccess()) {
                return SequenceResponse.SequenceResult.failure(singleResponse.getErrorMessage());
            }
            
            if (startSeq == null) {
                startSeq = singleResponse.getSeq();
            }
        }
        
        return SequenceResponse.SequenceResult.success(startSeq, count);
    }



    /**
     * 获取统计信息
     * 
     * @return 统计信息
     */
    public SequenceResponse.Stats getStats() {
        SequenceResponse.Stats stats = new SequenceResponse.Stats();
        stats.setTotalGenerated(totalGenerated.get());
        
        // 计算成功率
        long totalReq = totalRequests.get();
        long totalErr = totalErrors.get();
        double successRate = totalReq > 0 ? (double) (totalReq - totalErr) / totalReq : 0.0;
        
        Map<String, Object> details = new HashMap<>();
        details.put("totalRequests", totalReq);
        details.put("totalErrors", totalErr);
        details.put("successRate", successRate);
        details.put("sectionCount", SectionIdGenerator.getSectionCount());
        
        stats.setDetails(details);
        
        return stats;
    }
}
