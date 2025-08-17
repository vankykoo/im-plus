package com.vanky.im.sequence.client;

import com.vanky.im.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息服务 Feign 客户端降级处理
 * 遵循OCP开放封闭原则，通过降级处理扩展系统的容错能力
 * 提供统一的异常处理和降级策略，提高系统可靠性
 * 体现系统的容错能力和用户体验优化
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Slf4j
@Component
public class MessageServiceClientFallback implements MessageServiceClient {
    
    @Override
    public ApiResponse<MaxSeqResponse> getMaxSeqByUserId(String userId) {
        log.warn("消息服务不可用，使用降级策略获取用户最大序列号 - 用户ID: {}", userId);
        // 降级返回默认值0，确保序列号生成服务能够继续工作
        return ApiResponse.success(new MaxSeqResponse(0L));
    }
    
    @Override
    public ApiResponse<MaxSeqResponse> getMaxSeqByConversationId(String conversationId) {
        log.warn("消息服务不可用，使用降级策略获取会话最大序列号 - 会话ID: {}", conversationId);
        // 降级返回默认值0，确保序列号生成服务能够继续工作
        return ApiResponse.success(new MaxSeqResponse(0L));
    }
    
    @Override
    public ApiResponse<HealthResponse> healthCheck() {
        log.warn("消息服务不可用，使用降级策略返回健康检查结果");
        // 降级返回DOWN状态，表明服务不可用
        return ApiResponse.success(new HealthResponse("DOWN"));
    }
}
