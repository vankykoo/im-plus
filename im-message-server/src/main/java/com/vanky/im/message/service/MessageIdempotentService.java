package com.vanky.im.message.service;

import com.vanky.im.common.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 消息幂等性服务
 * 基于client_seq实现消息处理的幂等性，避免重复处理相同消息
 * 
 * @author vanky
 * @create 2025-08-05
 */
@Slf4j
@Service
public class MessageIdempotentService {
    
    // ========== 配置常量 ==========
    // 使用统一的Redis键常量管理
    
    // ========== 核心组件 ==========
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // ========== 公共方法 ==========
    
    /**
     * 检查消息是否已经处理过（幂等性检查）
     * @param clientSeq 客户端序列号
     * @return 如果已处理过，返回之前的处理结果；否则返回null
     */
    public IdempotentResult checkIdempotent(String clientSeq) {
        if (clientSeq == null || clientSeq.trim().isEmpty()) {
            log.debug("客户端序列号为空，跳过幂等性检查");
            return null;
        }
        
        String redisKey = RedisKeyConstants.getMessageIdempotentKey(clientSeq);
        
        try {
            // 从Redis中获取之前的处理结果
            Object result = redisTemplate.opsForValue().get(redisKey);
            
            if (result != null && result instanceof IdempotentResult) {
                IdempotentResult idempotentResult = (IdempotentResult) result;
                log.info("检测到重复消息 - 客户端序列号: {}, 之前的消息ID: {}, 序列号: {}",
                        clientSeq, idempotentResult.getMsgId(), idempotentResult.getSeq());
                return idempotentResult;
            }
            
            log.debug("幂等性检查通过，消息未处理过 - 客户端序列号: {}", clientSeq);
            return null;
            
        } catch (Exception e) {
            // Redis操作失败时的降级策略：记录错误但不阻塞消息处理
            log.error("幂等性检查Redis操作失败，降级为允许处理 - 客户端序列号: {}", clientSeq, e);
            return null;
        }
    }
    
    /**
     * 记录消息处理结果（用于幂等性）
     * @param clientSeq 客户端序列号
     * @param msgId 服务端消息ID
     * @param seq 服务端序列号
     */
    public void recordIdempotent(String clientSeq, String msgId, Long seq) {
        if (clientSeq == null || clientSeq.trim().isEmpty()) {
            log.debug("客户端序列号为空，跳过幂等性记录");
            return;
        }
        
        if (msgId == null || seq == null) {
            log.warn("消息ID或序列号为空，无法记录幂等性 - 客户端序列号: {}", clientSeq);
            return;
        }
        
        String redisKey = RedisKeyConstants.getMessageIdempotentKey(clientSeq);
        IdempotentResult result = new IdempotentResult(msgId, seq, System.currentTimeMillis());
        
        try {
            // 将处理结果存储到Redis，设置TTL为5分钟
            redisTemplate.opsForValue().set(redisKey, result, RedisKeyConstants.MESSAGE_IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS);
            
            log.info("记录消息幂等性成功 - 客户端序列号: {}, 消息ID: {}, 序列号: {}, TTL: {}秒",
                    clientSeq, msgId, seq, RedisKeyConstants.MESSAGE_IDEMPOTENT_TTL_SECONDS);
                    
        } catch (Exception e) {
            // Redis操作失败时记录错误，但不影响主流程
            log.error("记录消息幂等性Redis操作失败 - 客户端序列号: {}, 消息ID: {}, 序列号: {}",
                    clientSeq, msgId, seq, e);
        }
    }
    
    /**
     * 删除幂等性记录（用于测试或特殊场景）
     * @param clientSeq 客户端序列号
     * @return 是否删除成功
     */
    public boolean removeIdempotent(String clientSeq) {
        if (clientSeq == null || clientSeq.trim().isEmpty()) {
            return false;
        }
        
        String redisKey = RedisKeyConstants.getMessageIdempotentKey(clientSeq);
        
        try {
            Boolean deleted = redisTemplate.delete(redisKey);
            log.info("删除消息幂等性记录 - 客户端序列号: {}, 删除结果: {}", clientSeq, deleted);
            return Boolean.TRUE.equals(deleted);
            
        } catch (Exception e) {
            log.error("删除消息幂等性记录失败 - 客户端序列号: {}", clientSeq, e);
            return false;
        }
    }
    
    /**
     * 获取幂等性记录的剩余TTL
     * @param clientSeq 客户端序列号
     * @return 剩余TTL（秒），-1表示不存在或永不过期，-2表示已过期
     */
    public long getIdempotentTTL(String clientSeq) {
        if (clientSeq == null || clientSeq.trim().isEmpty()) {
            return -2;
        }
        
        String redisKey = RedisKeyConstants.getMessageIdempotentKey(clientSeq);
        
        try {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            return ttl != null ? ttl : -2;
            
        } catch (Exception e) {
            log.error("获取消息幂等性TTL失败 - 客户端序列号: {}", clientSeq, e);
            return -2;
        }
    }
    
    // ========== 私有方法 ==========
    
    // ========== 内部数据类 ==========
    
    /**
     * 幂等性结果数据类
     */
    public static class IdempotentResult {
        private String msgId;           // 服务端消息ID
        private Long seq;               // 服务端序列号
        private long processTime;       // 处理时间戳
        
        public IdempotentResult() {}
        
        public IdempotentResult(String msgId, Long seq, long processTime) {
            this.msgId = msgId;
            this.seq = seq;
            this.processTime = processTime;
        }
        
        // Getter和Setter方法
        public String getMsgId() {
            return msgId;
        }
        
        public void setMsgId(String msgId) {
            this.msgId = msgId;
        }
        
        public Long getSeq() {
            return seq;
        }
        
        public void setSeq(Long seq) {
            this.seq = seq;
        }
        
        public long getProcessTime() {
            return processTime;
        }
        
        public void setProcessTime(long processTime) {
            this.processTime = processTime;
        }
        
        @Override
        public String toString() {
            return String.format("IdempotentResult{msgId='%s', seq=%d, processTime=%d}",
                    msgId, seq, processTime);
        }
    }
}
