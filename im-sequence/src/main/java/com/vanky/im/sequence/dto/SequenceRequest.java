package com.vanky.im.sequence.dto;

import lombok.Data;


import java.util.List;

/**
 * 序列号请求DTO
 * 
 * @author vanky
 * @since 2025-08-11
 */
public class SequenceRequest {

    /**
     * 单个序列号请求
     */
    @Data
    public static class Single {
        /**
         * 业务key，如 "user_12345" 或 "group_67890"
         */
        private String key;
    }

    /**
     * 批量序列号请求
     */
    @Data
    public static class Batch {
        /**
         * 业务key列表
         */
        private List<String> keys;

        /**
         * 每个key需要的序列号数量（可选，默认为1）
         */
        private Integer count = 1;
    }
}
