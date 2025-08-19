package com.vanky.im.message.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @author vanky
 * @date 2024/7/21
 */
public class SequenceResponse {

    @Data
    public static class BaseResponse {
        private Boolean success;
        private String errorMessage;
    }

    @Data
    public static class Single extends BaseResponse {
        private Long seq;

        public static Single success(Long seq) {
            Single response = new Single();
            response.setSuccess(true);
            response.setSeq(seq);
            return response;
        }

        public static Single failure(String errorMessage) {
            Single response = new Single();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    @Data
    public static class Batch extends BaseResponse {
        private Map<String, SequenceResult> results;

        public static Batch success(Map<String, SequenceResult> results) {
            Batch response = new Batch();
            response.setSuccess(true);
            response.setResults(results);
            return response;
        }

        public static Batch failure(String errorMessage) {
            Batch response = new Batch();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    @Data
    public static class SequenceResult {
        private Boolean success;
        private Long startSeq;
        private Integer count;
        private String errorMessage;

        public static SequenceResult success(Long startSeq, Integer count) {
            SequenceResult result = new SequenceResult();
            result.setSuccess(true);
            result.setStartSeq(startSeq);
            result.setCount(count);
            return result;
        }

        public static SequenceResult failure(String errorMessage) {
            SequenceResult result = new SequenceResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }
    }

    @Data
    public static class Health {
        private String status;
        private Map<String, Object> details;
    }

    @Data
    public static class Stats {
        private long totalGenerated;
        private long totalErrors;
        private double tps;
        private Map<String, Object> details;
    }
}