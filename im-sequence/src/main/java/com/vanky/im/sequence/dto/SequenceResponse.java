package com.vanky.im.sequence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 序列号响应DTO
 * 
 * @author vanky
 * @since 2025-08-11
 */
public class SequenceResponse {

    /**
     * 单个序列号响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Single {
        /**
         * 生成的序列号
         */
        private Long seq;

        /**
         * 是否成功
         */
        private Boolean success;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * 创建成功响应
         */
        public static Single success(Long seq) {
            return new Single(seq, true, null);
        }

        /**
         * 创建失败响应
         */
        public static Single failure(String errorMessage) {
            return new Single(null, false, errorMessage);
        }
    }

    /**
     * 批量序列号响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Batch {
        /**
         * 每个key对应的序列号结果
         * key -> SequenceResult
         */
        private Map<String, SequenceResult> results;

        /**
         * 是否全部成功
         */
        private Boolean success;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * 创建成功响应
         */
        public static Batch success(Map<String, SequenceResult> results) {
            return new Batch(results, true, null);
        }

        /**
         * 创建失败响应
         */
        public static Batch failure(String errorMessage) {
            return new Batch(null, false, errorMessage);
        }
    }

    /**
     * 序列号结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SequenceResult {
        /**
         * 起始序列号
         */
        private Long startSeq;

        /**
         * 序列号数量
         */
        private Integer count;

        /**
         * 是否成功
         */
        private Boolean success;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * 创建成功结果
         */
        public static SequenceResult success(Long startSeq, Integer count) {
            return new SequenceResult(startSeq, count, true, null);
        }

        /**
         * 创建失败结果
         */
        public static SequenceResult failure(String errorMessage) {
            return new SequenceResult(null, null, false, errorMessage);
        }
    }

    /**
     * 健康检查响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Health {
        /**
         * 服务状态
         */
        private String status;

        /**
         * Redis状态
         */
        private String redis;

        /**
         * 数据库状态
         */
        private String database;

        /**
         * 详细信息
         */
        private Map<String, Object> details;
    }

    /**
     * 统计信息响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        /**
         * 总生成数量
         */
        private Long totalGenerated;

        /**
         * 分段数量
         */
        private Long sectionsCount;

        /**
         * Redis命中率
         */
        private Double redisHitRate;

        /**
         * 平均响应时间（毫秒）
         */
        private Double avgResponseTime;

        /**
         * 详细统计信息
         */
        private Map<String, Object> details;
    }
}
