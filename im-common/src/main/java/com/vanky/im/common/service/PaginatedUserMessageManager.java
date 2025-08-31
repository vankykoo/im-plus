package com.vanky.im.common.service;

import com.vanky.im.common.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分页用户消息链管理器
 * 解决Redis大KEY问题，将用户消息链按页分割存储
 * 
 * 设计原则：
 * - KISS: 使用简单的分页策略，按消息数量分页
 * - SOLID-S: 单一职责，只负责用户消息链的分页管理
 * - DRY: 抽象通用的分页操作，避免重复代码
 * - YAGNI: 只实现必要的分页功能，不过度设计
 * 
 * @author vanky
 * @since 2025-08-31
 */
@Slf4j
@Component
public class PaginatedUserMessageManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每页消息数量，设计为100条
     * 原则：DRY - 使用统一的常量定义
     */
    private static final int PAGE_SIZE = RedisKeyConstants.USER_MSG_PAGE_SIZE;

    /**
     * 用户消息链分页键前缀
     * 原则：DRY - 使用统一的常量定义
     */
    private static final String PAGE_KEY_PREFIX = RedisKeyConstants.USER_MSG_LIST_PAGE_PREFIX;

    /**
     * 用户消息链元数据键前缀（记录总消息数、当前页等信息）
     * 原则：DRY - 使用统一的常量定义
     */
    private static final String META_KEY_PREFIX = RedisKeyConstants.USER_MSG_META_PREFIX;

    /**
     * 添加消息到用户消息链
     * 原则：SOLID-S - 单一职责，只负责添加操作
     * 
     * @param userId 用户ID
     * @param msgId 消息ID
     * @param seq 序列号
     * @param maxTotalSize 最大总消息数
     */
    public void addMessage(String userId, String msgId, Long seq, int maxTotalSize) {
        if (userId == null || userId.trim().isEmpty() || msgId == null || seq == null) {
            log.warn("参数无效，无法添加消息到用户消息链 - userId: {}, msgId: {}, seq: {}", 
                    userId, msgId, seq);
            return;
        }

        try {
            // 计算消息应该存储在哪一页
            int pageNo = calculatePageNumber(seq);
            String pageKey = getPageKey(userId, pageNo);
            
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            
            // 添加消息到对应页面
            zSetOps.add(pageKey, msgId, seq.doubleValue());
            
            // 设置页面过期时间（避免内存泄漏）
            redisTemplate.expire(pageKey, RedisKeyConstants.CONVERSATION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            
            // 更新用户消息元数据
            updateUserMessageMetadata(userId, seq, maxTotalSize);
            
            log.debug("添加消息到分页用户消息链成功 - userId: {}, msgId: {}, seq: {}, 页号: {}", 
                     userId, msgId, seq, pageNo);
            
        } catch (Exception e) {
            log.error("添加消息到分页用户消息链失败 - userId: {}, msgId: {}, seq: {}", 
                     userId, msgId, seq, e);
            throw new RuntimeException("添加消息到分页用户消息链失败", e);
        }
    }

    /**
     * 获取用户消息链（支持分页查询）
     * 原则：KISS - 简单的范围查询实现
     * 
     * @param userId 用户ID
     * @param start 开始位置（全局位置）
     * @param end 结束位置（全局位置）
     * @return 消息ID列表（按序列号倒序）
     */
    public List<String> getUserMessages(String userId, long start, long end) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 获取用户消息元数据
            UserMessageMetadata metadata = getUserMessageMetadata(userId);
            if (metadata == null || metadata.getTotalCount() == 0) {
                log.debug("用户无消息记录 - userId: {}", userId);
                return Collections.emptyList();
            }
            
            // 计算需要查询的页面范围
            Set<Integer> pageNumbers = calculatePageRange(start, end, metadata.getMaxSeq());
            
            // 分页查询并合并结果
            List<MessageEntry> allMessages = new ArrayList<>();
            
            for (Integer pageNo : pageNumbers) {
                String pageKey = getPageKey(userId, pageNo);
                Set<ZSetOperations.TypedTuple<Object>> pageMessages = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(pageKey, 0, -1);
                
                if (pageMessages != null && !pageMessages.isEmpty()) {
                    for (ZSetOperations.TypedTuple<Object> tuple : pageMessages) {
                        if (tuple.getValue() instanceof String && tuple.getScore() != null) {
                            long seq = tuple.getScore().longValue();
                            // 只返回指定范围内的消息
                            if (seq >= start && seq <= end) {
                                allMessages.add(new MessageEntry((String) tuple.getValue(), seq));
                            }
                        }
                    }
                }
            }
            
            // 按序列号倒序排序，并提取消息ID
            List<String> result = allMessages.stream()
                .sorted((a, b) -> Long.compare(b.getSeq(), a.getSeq()))
                .map(MessageEntry::getMsgId)
                .collect(Collectors.toList());
            
            log.debug("获取用户分页消息链成功 - userId: {}, 查询范围: [{}, {}], 返回数量: {}", 
                     userId, start, end, result.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("获取用户分页消息链失败 - userId: {}, 范围: [{}, {}]", userId, start, end, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户最新的N条消息
     * 原则：YAGNI - 保留原有接口，内部使用分页实现
     * 
     * @param userId 用户ID
     * @param count 消息数量
     * @return 最新的N条消息ID列表
     */
    public List<String> getUserLatestMessages(String userId, int count) {
        if (userId == null || userId.trim().isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        try {
            UserMessageMetadata metadata = getUserMessageMetadata(userId);
            if (metadata == null || metadata.getTotalCount() == 0) {
                return Collections.emptyList();
            }
            
            // 从最大序列号开始往前查询
            long endSeq = metadata.getMaxSeq();
            long startSeq = Math.max(1, endSeq - count + 1);
            
            List<String> messages = getUserMessages(userId, startSeq, endSeq);
            
            // 限制返回数量
            if (messages.size() > count) {
                messages = messages.subList(0, count);
            }
            
            log.debug("获取用户最新消息成功 - userId: {}, 请求数量: {}, 返回数量: {}", 
                     userId, count, messages.size());
            
            return messages;
            
        } catch (Exception e) {
            log.error("获取用户最新消息失败 - userId: {}, count: {}", userId, count, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清理用户的旧消息页面
     * 原则：SOLID-S - 专门负责清理操作
     * 
     * @param userId 用户ID
     * @param maxTotalSize 最大保留消息数
     */
    public void cleanupOldMessages(String userId, int maxTotalSize) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        try {
            UserMessageMetadata metadata = getUserMessageMetadata(userId);
            if (metadata == null || metadata.getTotalCount() <= maxTotalSize) {
                return; // 无需清理
            }
            
            // 计算需要保留的最小序列号
            long minKeepSeq = metadata.getMaxSeq() - maxTotalSize + 1;
            int minKeepPage = calculatePageNumber(minKeepSeq);
            
            // 删除旧页面
            int cleanedPages = 0;
            for (int pageNo = 0; pageNo < minKeepPage; pageNo++) {
                String pageKey = getPageKey(userId, pageNo);
                Boolean deleted = redisTemplate.delete(pageKey);
                if (Boolean.TRUE.equals(deleted)) {
                    cleanedPages++;
                }
            }
            
            // 更新元数据
            if (cleanedPages > 0) {
                metadata.setTotalCount(maxTotalSize);
                metadata.setMinSeq(minKeepSeq);
                saveUserMessageMetadata(userId, metadata);
                
                log.debug("清理用户旧消息页面完成 - userId: {}, 清理页面数: {}, 保留消息数: {}", 
                         userId, cleanedPages, maxTotalSize);
            }
            
        } catch (Exception e) {
            log.error("清理用户旧消息页面失败 - userId: {}", userId, e);
        }
    }

    /**
     * 计算序列号对应的页号
     * 原则：KISS - 简单的除法计算
     * 
     * @param seq 序列号
     * @return 页号
     */
    private int calculatePageNumber(Long seq) {
        return (int) ((seq - 1) / PAGE_SIZE);
    }

    /**
     * 计算查询范围需要的页面集合
     * 
     * @param start 开始序列号
     * @param end 结束序列号
     * @param maxSeq 最大序列号
     * @return 页面号集合
     */
    private Set<Integer> calculatePageRange(long start, long end, long maxSeq) {
        // 确保查询范围在有效范围内
        start = Math.max(1, start);
        end = Math.min(maxSeq, end);
        
        int startPage = calculatePageNumber(start);
        int endPage = calculatePageNumber(end);
        
        Set<Integer> pages = new HashSet<>();
        for (int page = startPage; page <= endPage; page++) {
            pages.add(page);
        }
        
        return pages;
    }

    /**
     * 获取页面键
     * 原则：DRY - 统一的键生成逻辑
     * 
     * @param userId 用户ID
     * @param pageNo 页号
     * @return 页面键
     */
    private String getPageKey(String userId, int pageNo) {
        return PAGE_KEY_PREFIX + userId + ":" + pageNo;
    }

    /**
     * 获取元数据键
     * 
     * @param userId 用户ID
     * @return 元数据键
     */
    private String getMetaKey(String userId) {
        return META_KEY_PREFIX + userId;
    }

    /**
     * 更新用户消息元数据
     * 
     * @param userId 用户ID
     * @param seq 新消息序列号
     * @param maxTotalSize 最大总消息数
     */
    private void updateUserMessageMetadata(String userId, Long seq, int maxTotalSize) {
        String metaKey = getMetaKey(userId);
        
        UserMessageMetadata metadata = getUserMessageMetadata(userId);
        if (metadata == null) {
            metadata = new UserMessageMetadata();
            metadata.setMinSeq(seq);
            metadata.setMaxSeq(seq);
            metadata.setTotalCount(1);
        } else {
            metadata.setMaxSeq(Math.max(metadata.getMaxSeq(), seq));
            metadata.setTotalCount(metadata.getTotalCount() + 1);
            
            // 如果超过最大限制，启动清理
            if (metadata.getTotalCount() > maxTotalSize) {
                cleanupOldMessages(userId, maxTotalSize);
            }
        }
        
        saveUserMessageMetadata(userId, metadata);
    }

    /**
     * 获取用户消息元数据
     * 
     * @param userId 用户ID
     * @return 元数据对象
     */
    private UserMessageMetadata getUserMessageMetadata(String userId) {
        String metaKey = getMetaKey(userId);
        
        try {
            Object metaObj = redisTemplate.opsForValue().get(metaKey);
            if (metaObj instanceof UserMessageMetadata) {
                return (UserMessageMetadata) metaObj;
            }
        } catch (Exception e) {
            log.error("获取用户消息元数据失败 - userId: {}", userId, e);
        }
        
        return null;
    }

    /**
     * 保存用户消息元数据
     * 
     * @param userId 用户ID
     * @param metadata 元数据对象
     */
    private void saveUserMessageMetadata(String userId, UserMessageMetadata metadata) {
        String metaKey = getMetaKey(userId);
        
        try {
            redisTemplate.opsForValue().set(metaKey, metadata, 
                RedisKeyConstants.CONVERSATION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("保存用户消息元数据失败 - userId: {}", userId, e);
        }
    }

    /**
     * 消息条目类
     * 内部数据结构，用于排序
     */
    private static class MessageEntry {
        private final String msgId;
        private final Long seq;

        public MessageEntry(String msgId, Long seq) {
            this.msgId = msgId;
            this.seq = seq;
        }

        public String getMsgId() {
            return msgId;
        }

        public Long getSeq() {
            return seq;
        }
    }

    /**
     * 用户消息元数据类
     * 记录用户消息的基本统计信息
     */
    public static class UserMessageMetadata {
        private Long minSeq;      // 最小序列号
        private Long maxSeq;      // 最大序列号
        private Integer totalCount; // 总消息数

        public UserMessageMetadata() {}

        public Long getMinSeq() {
            return minSeq;
        }

        public void setMinSeq(Long minSeq) {
            this.minSeq = minSeq;
        }

        public Long getMaxSeq() {
            return maxSeq;
        }

        public void setMaxSeq(Long maxSeq) {
            this.maxSeq = maxSeq;
        }

        public Integer getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }

        @Override
        public String toString() {
            return String.format("UserMessageMetadata{minSeq=%d, maxSeq=%d, totalCount=%d}", 
                                minSeq, maxSeq, totalCount);
        }
    }
}
