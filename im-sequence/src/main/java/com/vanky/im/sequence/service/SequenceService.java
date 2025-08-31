package com.vanky.im.sequence.service;

import com.vanky.im.sequence.config.SequenceConfig;
import com.vanky.im.sequence.constant.SequenceConstants;
import com.vanky.im.sequence.client.MessageClient;
import com.vanky.im.sequence.dto.SequenceRequest;
import com.vanky.im.sequence.dto.SequenceResponse;
import com.vanky.im.sequence.util.SectionIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    @Autowired
    private MessageClient messageClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

            // 检查是否需要恢复序列号
            long initialValue = 0L;
            if (sequenceConfig.getRecovery().isEnabled()) {
                initialValue = checkAndRecoverSequence(redisKey, sectionKey);
            }

            // 执行Lua脚本获取序列号
            List<String> luaResult = luaScriptService.executeGetNextSeq(
                    redisKey, sequenceConfig.getSection().getStepSize(), initialValue);
            
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

    /**
     * 检查并恢复序列号（带分布式锁保护，防止缓存击穿）
     * 如果Redis Key不存在，从sequence_section表中查询该分段的最大序列号作为初始值
     * 
     * 安全加固说明：
     * - 使用分布式锁防止并发恢复导致的数据不一致
     * - 双重检查机制确保恢复过程的原子性
     * - 异常降级处理保证服务可用性
     *
     * @param redisKey Redis键
     * @param sectionKey 分段键，如 "u_17" 或 "c_456"
     * @return 初始值，如果不需要恢复则返回0
     */
    private long checkAndRecoverSequence(String redisKey, String sectionKey) {
        try {
            // 第一次检查Redis Key是否存在
            Boolean exists = stringRedisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                log.debug("Redis Key存在，无需恢复 - redisKey: {}", redisKey);
                return 0L;
            }

            // Key不存在，使用分布式锁防止并发恢复
            String lockKey = "seq:recovery:lock:" + sectionKey;
            String lockValue = String.valueOf(System.currentTimeMillis());
            
            Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofSeconds(30));
            
            if (Boolean.TRUE.equals(lockAcquired)) {
                try {
                    // 获取锁成功，双重检查Redis Key（防止在等待锁期间其他线程已经恢复）
                    exists = stringRedisTemplate.hasKey(redisKey);
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("锁内二次检查发现Key已存在，无需恢复 - redisKey: {}", redisKey);
                        return 0L;
                    }

                    // 执行恢复逻辑
                    log.info("开始恢复序列号 - redisKey: {}, sectionKey: {}", redisKey, sectionKey);
                    
                    Long maxSeq = persistenceService.recoverMaxSeq(sectionKey);
                    if (maxSeq == null || maxSeq < 0) {
                        maxSeq = 0L;
                    }

                    log.info("序列号恢复成功 - sectionKey: {}, 恢复的最大序列号: {}", sectionKey, maxSeq);
                    return maxSeq;
                    
                } finally {
                    // 释放分布式锁
                    releaseLock(lockKey, lockValue);
                }
            } else {
                // 获取锁失败，等待一段时间后重试检查Redis Key
                log.debug("获取恢复锁失败，等待后重试 - sectionKey: {}", sectionKey);
                
                for (int i = 0; i < 3; i++) {
                    try {
                        Thread.sleep(100); // 等待100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    exists = stringRedisTemplate.hasKey(redisKey);
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("等待期间Key已被其他线程恢复 - redisKey: {}", redisKey);
                        return 0L;
                    }
                }
                
                // 重试后仍未恢复，降级处理：直接从数据库查询但不持久化到Redis
                log.warn("获取恢复锁失败且重试无效，降级查询 - sectionKey: {}", sectionKey);
                Long maxSeq = persistenceService.recoverMaxSeq(sectionKey);
                return maxSeq != null && maxSeq >= 0 ? maxSeq : 0L;
            }

        } catch (Exception e) {
            log.error("恢复序列号失败 - redisKey: {}, sectionKey: {}", redisKey, sectionKey, e);
            return 0L;
        }
    }

    /**
     * 释放分布式锁
     * 使用Lua脚本确保只释放自己的锁
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            String luaScript = 
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('DEL', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                return connection.eval(luaScript.getBytes(), 
                                     org.springframework.data.redis.connection.ReturnType.INTEGER, 
                                     1, lockKey.getBytes(), lockValue.getBytes());
            });
            
            log.debug("释放恢复锁成功 - lockKey: {}", lockKey);
        } catch (Exception e) {
            log.error("释放恢复锁异常 - lockKey: {}", lockKey, e);
        }
    }
}
