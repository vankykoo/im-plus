package com.vanky.im.sequence.client;

import com.vanky.im.common.model.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 消息服务 Feign 客户端
 * 遵循SRP单一职责原则，专门负责与im-message-server服务的通信
 * 遵循ISP接口隔离原则，只定义当前明确需要的方法
 * 体现KISS原则，简化HTTP调用复杂性
 * 
 * @author vanky
 * @since 2025-08-14
 */
@FeignClient(
    name = "im-message-server",  // 使用Nacos服务名进行调用
    path = "/api/message/max-seq",
    fallback = MessageServiceClientFallback.class
)
public interface MessageServiceClient {
    
    /**
     * 根据用户ID查询最大序列号
     * 为序列号恢复服务提供用户维度的序列号查询接口
     * 
     * @param userId 用户ID
     * @return 最大序列号响应
     */
    @GetMapping("/user/{userId}")
    ApiResponse<MaxSeqResponse> getMaxSeqByUserId(@PathVariable("userId") String userId);
    
    /**
     * 根据会话ID查询最大序列号
     * 为序列号恢复服务提供会话维度的序列号查询接口
     * 
     * @param conversationId 会话ID
     * @return 最大序列号响应
     */
    @GetMapping("/conversation/{conversationId}")
    ApiResponse<MaxSeqResponse> getMaxSeqByConversationId(@PathVariable("conversationId") String conversationId);
    
    /**
     * 健康检查接口
     * 用于监控消息服务的可用性
     * 
     * @return 健康状态响应
     */
    @GetMapping("/health")
    ApiResponse<HealthResponse> healthCheck();
    
    /**
     * 最大序列号响应数据结构
     * 遵循KISS原则，保持数据结构简洁明了
     */
    class MaxSeqResponse {
        private Long maxSeq;
        
        public MaxSeqResponse() {}
        
        public MaxSeqResponse(Long maxSeq) {
            this.maxSeq = maxSeq;
        }
        
        public Long getMaxSeq() {
            return maxSeq;
        }
        
        public void setMaxSeq(Long maxSeq) {
            this.maxSeq = maxSeq;
        }
    }
    
    /**
     * 健康检查响应数据结构
     * 遵循YAGNI原则，只包含必要的状态信息
     */
    class HealthResponse {
        private String status;
        
        public HealthResponse() {}
        
        public HealthResponse(String status) {
            this.status = status;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
}
