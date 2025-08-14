package com.vanky.im.sequence.service;

import com.vanky.im.sequence.config.SequenceConfig;
import com.vanky.im.sequence.constant.SequenceConstants;
import com.vanky.im.sequence.entity.SequenceSection;
import com.vanky.im.sequence.mapper.SequenceSectionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 序列号持久化服务
 * 负责异步持久化序列号分段信息到数据库
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@Service
public class SequencePersistenceService {

    @Autowired
    private SequenceSectionMapper sequenceSectionMapper;

    @Autowired
    private SequenceConfig sequenceConfig;

    /**
     * 异步持久化单个分段的最大序列号
     *
     * @param sectionKey 分段键，如 u_123 或 c_456
     * @param maxSeq 最大序列号
     * @return 异步结果
     */
    @Async("sequencePersistenceExecutor")
    public CompletableFuture<Boolean> persistMaxSeqAsync(String sectionKey, Long maxSeq) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return persistMaxSeqWithRetry(sectionKey, maxSeq, 0);
            } catch (Exception e) {
                log.error("Failed to persist max seq for section: {}, maxSeq: {}",
                         sectionKey, maxSeq, e);
                return false;
            }
        });
    }

    /**
     * 带重试的持久化逻辑
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现高效的原子操作
     *
     * @param sectionKey 分段键
     * @param maxSeq 最大序列号
     * @param retryCount 当前重试次数
     * @return 是否成功
     */
    private boolean persistMaxSeqWithRetry(String sectionKey, Long maxSeq, int retryCount) {
        try {
            // 使用原子的 INSERT ... ON DUPLICATE KEY UPDATE 操作
            int affectedRows = sequenceSectionMapper.insertOrUpdateMaxSeq(
                    sectionKey,
                    maxSeq,
                    sequenceConfig.getSection().getStepSize()
            );

            if (affectedRows > 0) {
                log.debug("Successfully persisted max seq for section: {}, maxSeq: {}", sectionKey, maxSeq);
                return true;
            } else {
                log.warn("No rows affected for section: {}, maxSeq: {}", sectionKey, maxSeq);
                return false;
            }
        } catch (Exception e) {
            if (retryCount < SequenceConstants.Persistence.MAX_RETRY_TIMES) {
                // 指数退避重试
                long delay = SequenceConstants.Persistence.RETRY_INTERVAL_BASE * (1L << retryCount);
                log.warn("Persist failed, retrying in {}ms, attempt: {}/{}, section: {}",
                        delay, retryCount + 1, SequenceConstants.Persistence.MAX_RETRY_TIMES, sectionKey);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                return persistMaxSeqWithRetry(sectionKey, maxSeq, retryCount + 1);
            } else {
                log.error("Failed to persist after {} retries, section: {}, maxSeq: {}",
                         SequenceConstants.Persistence.MAX_RETRY_TIMES, sectionKey, maxSeq, e);
                return false;
            }
        }
    }

    /**
     * 批量持久化分段信息
     * 
     * @param sections 分段信息列表
     * @return 异步结果
     */
    @Async("sequencePersistenceExecutor")
    @Transactional
    public CompletableFuture<Integer> batchPersistAsync(List<SequenceSection> sections) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sections == null || sections.isEmpty()) {
                    return 0;
                }

                int successCount = 0;
                List<SequenceSection> batchList = new ArrayList<>();

                for (SequenceSection section : sections) {
                    batchList.add(section);

                    // 每100个批量处理一次
                    if (batchList.size() >= 100) {
                        successCount += processBatch(batchList);
                        batchList.clear();
                    }
                }

                // 处理剩余的
                if (!batchList.isEmpty()) {
                    successCount += processBatch(batchList);
                }

                log.info("Batch persist completed, total: {}, success: {}", sections.size(), successCount);
                return successCount;
            } catch (Exception e) {
                log.error("Failed to batch persist sections", e);
                return 0;
            }
        });
    }

    /**
     * 处理批量数据
     *
     * @param batchList 批量数据
     * @return 成功数量
     */
    private int processBatch(List<SequenceSection> batchList) {
        try {
            return sequenceSectionMapper.batchInsertOrUpdate(batchList);
        } catch (Exception e) {
            log.error("Failed to process batch, size: {}", batchList.size(), e);
            // 降级为单个处理
            int successCount = 0;
            for (SequenceSection section : batchList) {
                try {
                    int count = sequenceSectionMapper.insertOrUpdateMaxSeq(
                            section.getSectionKey(),
                            section.getMaxSeq(),
                            section.getStep());
                    if (count > 0) {
                        successCount++;
                    }
                } catch (Exception ex) {
                    log.error("Failed to update single section: {}", section.getSectionKey(), ex);
                }
            }
            return successCount;
        }
    }

    /**
     * 从数据库恢复分段的最大序列号（用于冷启动）
     *
     * @param sectionKey 分段键
     * @return 最大序列号，如果不存在则返回0
     */
    public Long recoverMaxSeq(String sectionKey) {
        try {
            SequenceSection section = sequenceSectionMapper.selectBySectionKey(sectionKey);
            return section != null ? section.getMaxSeq() : 0L;
        } catch (Exception e) {
            log.error("Failed to recover max seq for section: {}", sectionKey, e);
            return 0L;
        }
    }

    /**
     * 从业务消息表中查询最大序列号
     * 通过RPC调用消息服务获取业务表中的最大序列号
     *
     * @param businessKey 业务键，如 "user_12345" 或 "conversation_67890"
     * @return 最大序列号，查询失败时返回0
     */
    public Long getMaxSeqFromBusinessTable(String businessKey) {
        // 这个方法将在SequenceService中通过MessageClient实现
        // 这里只是预留接口，实际实现在SequenceService中
        log.debug("getMaxSeqFromBusinessTable called with businessKey: {}", businessKey);
        return 0L;
    }
}
