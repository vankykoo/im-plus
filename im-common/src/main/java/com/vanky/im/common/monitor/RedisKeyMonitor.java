package com.vanky.im.common.monitor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.service.PaginatedUserMessageManager;
import com.vanky.im.common.service.ShardedOnlineUserManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis键监控器
 * 监控Redis中的大KEY、热KEY问题，提供性能指标统计
 * 
 * 设计原则：
 * - KISS: 简单的定时检查机制
 * - SOLID-S: 单一职责，只负责监控和统计
 * - YAGNI: 只实现必要的监控功能
 * 
 * @author vanky
 * @since 2025-08-31
 */
@Slf4j
@Component
public class RedisKeyMonitor {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ShardedOnlineUserManager shardedOnlineUserManager;

    @Autowired
    private PaginatedUserMessageManager paginatedUserMessageManager;

    // 性能统计指标
    private final AtomicLong totalOnlineUsers = new AtomicLong(0);
    private final AtomicLong totalUserMessagePages = new AtomicLong(0);
    private final AtomicLong largeKeyWarnings = new AtomicLong(0);
    private final AtomicLong hotKeyWarnings = new AtomicLong(0);

    /**
     * 定期检查大KEY问题
     * 每5分钟执行一次检查
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void checkLargeKeys() {
        log.debug("开始检查Redis大KEY问题");
        
        try {
            Map<String, Object> report = new HashMap<>();
            
            // 1. 检查用户消息链分页情况
            int messagePageCount = checkUserMessagePages();
            report.put("userMessagePages", messagePageCount);
            
            // 2. 检查在线用户分片情况
            Map<String, Object> onlineUserStats = checkOnlineUserShards();
            report.put("onlineUserShards", onlineUserStats);
            
            // 3. 检查其他可能的大KEY
            Map<String, Object> otherKeys = checkOtherLargeKeys();
            report.put("otherLargeKeys", otherKeys);
            
            // 更新统计指标
            totalUserMessagePages.set(messagePageCount);
            
            // 如果有异常情况，记录警告
            if (messagePageCount > 10000) { // 假设阈值为10000页
                largeKeyWarnings.incrementAndGet();
                log.warn("检测到用户消息页面数量过多: {}", messagePageCount);
            }
            
            // 定期输出监控报告
            if (System.currentTimeMillis() % 1800000 < 300000) { // 每30分钟输出一次
                log.info("Redis KEY监控报告: {}", report);
            }
            
        } catch (Exception e) {
            log.error("检查Redis大KEY问题失败", e);
        }
    }

    /**
     * 定期检查热KEY问题
     * 每10分钟执行一次检查
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void checkHotKeys() {
        log.debug("开始检查Redis热KEY问题");
        
        try {
            // 检查在线用户分片的负载均衡情况
            Map<Integer, Long> shardSizes = new HashMap<>();
            long totalUsers = 0;
            
            for (int i = 0; i < RedisKeyConstants.ONLINE_USERS_SHARD_COUNT; i++) {
                String shardKey = RedisKeyConstants.ONLINE_USERS_SHARD_PREFIX + i;
                Long size = redisTemplate.opsForSet().size(shardKey);
                long shardSize = size != null ? size : 0;
                shardSizes.put(i, shardSize);
                totalUsers += shardSize;
            }
            
            // 检查负载均衡情况
            if (totalUsers > 0) {
                double avgSize = (double) totalUsers / RedisKeyConstants.ONLINE_USERS_SHARD_COUNT;
                boolean unbalanced = false;
                
                for (Map.Entry<Integer, Long> entry : shardSizes.entrySet()) {
                    double ratio = entry.getValue() / avgSize;
                    if (ratio > 2.0 || ratio < 0.5) { // 偏差超过2倍认为不均衡
                        unbalanced = true;
                        log.warn("检测到在线用户分片{}负载不均衡: 实际={}, 平均={}, 比例={}", 
                                entry.getKey(), entry.getValue(), avgSize, ratio);
                    }
                }
                
                if (unbalanced) {
                    hotKeyWarnings.incrementAndGet();
                }
            }
            
            // 更新总在线用户数统计
            totalOnlineUsers.set(totalUsers);
            
            log.debug("在线用户分片检查完成 - 总用户数: {}, 分片详情: {}", totalUsers, shardSizes);
            
        } catch (Exception e) {
            log.error("检查Redis热KEY问题失败", e);
        }
    }

    /**
     * 获取性能统计指标
     * 提供给监控系统使用
     * 
     * @return 性能指标Map
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("totalOnlineUsers", totalOnlineUsers.get());
        metrics.put("totalUserMessagePages", totalUserMessagePages.get());
        metrics.put("largeKeyWarnings", largeKeyWarnings.get());
        metrics.put("hotKeyWarnings", hotKeyWarnings.get());
        
        // 分片负载情况
        Map<String, Long> shardMetrics = new HashMap<>();
        for (int i = 0; i < RedisKeyConstants.ONLINE_USERS_SHARD_COUNT; i++) {
            String shardKey = RedisKeyConstants.ONLINE_USERS_SHARD_PREFIX + i;
            Long size = redisTemplate.opsForSet().size(shardKey);
            shardMetrics.put("shard_" + i, size != null ? size : 0);
        }
        metrics.put("onlineUserShards", shardMetrics);
        
        return metrics;
    }

    /**
     * 手动触发大KEY清理
     * 提供给运维使用
     */
    public void cleanupLargeKeys() {
        log.info("开始手动清理大KEY");
        
        try {
            // 清理过期的消息页面
            int cleanedPages = cleanupExpiredMessagePages();
            log.info("清理过期消息页面完成 - 清理数量: {}", cleanedPages);
            
            // 触发在线用户过期清理
            long cleanedUsers = triggerOnlineUserCleanup();
            log.info("清理过期在线用户完成 - 清理数量: {}", cleanedUsers);
            
        } catch (Exception e) {
            log.error("手动清理大KEY失败", e);
        }
    }

    /**
     * 检查用户消息页面数量
     * 
     * @return 总页面数量
     */
    private int checkUserMessagePages() {
        try {
            Set<String> keys = redisTemplate.keys(RedisKeyConstants.USER_MSG_LIST_PAGE_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("检查用户消息页面失败", e);
            return 0;
        }
    }

    /**
     * 检查在线用户分片情况
     * 
     * @return 分片统计信息
     */
    private Map<String, Object> checkOnlineUserShards() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long totalCount = shardedOnlineUserManager.getOnlineUserCount();
            stats.put("totalOnlineUsers", totalCount);
            stats.put("shardCount", RedisKeyConstants.ONLINE_USERS_SHARD_COUNT);
            stats.put("averagePerShard", totalCount / (double) RedisKeyConstants.ONLINE_USERS_SHARD_COUNT);
            
        } catch (Exception e) {
            log.error("检查在线用户分片失败", e);
        }
        
        return stats;
    }

    /**
     * 检查其他可能的大KEY
     * 
     * @return 其他大KEY统计
     */
    private Map<String, Object> checkOtherLargeKeys() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 检查会话相关键
            Set<String> sessionKeys = redisTemplate.keys(RedisKeyConstants.USER_SESSION_KEY_PREFIX + "*");
            stats.put("sessionKeyCount", sessionKeys != null ? sessionKeys.size() : 0);
            
            // 检查消息缓存键
            Set<String> msgKeys = redisTemplate.keys(RedisKeyConstants.MESSAGE_CACHE_PREFIX + "*");
            stats.put("messageCacheCount", msgKeys != null ? msgKeys.size() : 0);
            
            // 检查会话列表键
            Set<String> convKeys = redisTemplate.keys(RedisKeyConstants.USER_CONVERSATION_LIST_PREFIX + "*");
            stats.put("conversationListCount", convKeys != null ? convKeys.size() : 0);
            
        } catch (Exception e) {
            log.error("检查其他大KEY失败", e);
        }
        
        return stats;
    }

    /**
     * 清理过期的消息页面
     * 
     * @return 清理的页面数量
     */
    private int cleanupExpiredMessagePages() {
        int cleanedCount = 0;
        
        try {
            Set<String> pageKeys = redisTemplate.keys(RedisKeyConstants.USER_MSG_LIST_PAGE_PREFIX + "*");
            if (pageKeys != null && !pageKeys.isEmpty()) {
                for (String key : pageKeys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl < 0) { // 没有设置TTL或已过期
                        Boolean deleted = redisTemplate.delete(key);
                        if (Boolean.TRUE.equals(deleted)) {
                            cleanedCount++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("清理过期消息页面失败", e);
        }
        
        return cleanedCount;
    }

    /**
     * 触发在线用户清理
     * 
     * @return 清理的用户数量
     */
    private long triggerOnlineUserCleanup() {
        // 这里可以触发UserOfflineService的清理方法
        // 暂时返回0，具体实现需要注入UserOfflineService
        return 0;
    }

    /**
     * 重置统计指标
     * 提供给测试或运维使用
     */
    public void resetMetrics() {
        totalOnlineUsers.set(0);
        totalUserMessagePages.set(0);
        largeKeyWarnings.set(0);
        hotKeyWarnings.set(0);
        
        log.info("Redis监控指标已重置");
    }
}
