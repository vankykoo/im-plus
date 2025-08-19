package com.vanky.im.message.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author vanky
 * @date 2024/7/21
 */
public class SequenceRequest {

    @Data
    @NoArgsConstructor
    public static class Single {
        private String key;

        public Single(String key) {
            this.key = key;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Batch {
        private Map<String, Integer> keys;

        public Batch(Map<String, Integer> keys) {
            this.keys = keys;
        }
    }
}