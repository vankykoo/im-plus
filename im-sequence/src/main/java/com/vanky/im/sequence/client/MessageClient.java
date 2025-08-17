package com.vanky.im.sequence.client;

import com.vanky.im.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息服务客户端
 * 负责调用 im-message-server 服务查询最大序列号
 * 重构为使用 Feign 客户端，体现 LSP 里氏替换原则
 * 保持相同的方法签名，确保调用方无需修改
 *
 * @author vanky
 * @since 2025-08-14
 * @updated 2025-08-14 - 重构为使用 Feign 客户端
 */
@Slf4j
@Component
public class MessageClient {

    @Autowired
    private MessageServiceClient messageServiceClient;

    /**
     * 根据业务key查询最大序列号
     * 使用 Feign 客户端替换原有的 HttpClient 实现
     * 保持相同的方法签名，体现 LSP 里氏替换原则
     *
     * @param businessKey 业务key，如 "user_12345" 或 "conversation_67890"
     * @return 最大序列号，失败时返回0
     */
    public Long getMaxSeqByBusinessKey(String businessKey) {
        try {
            if (businessKey == null || businessKey.trim().isEmpty()) {
                log.warn("业务key为空，返回默认值0");
                return 0L;
            }

            // 根据业务key类型调用不同的Feign接口
            if (businessKey.startsWith("user_")) {
                String userId = businessKey.substring(5); // 去掉 "user_" 前缀
                ApiResponse<MessageServiceClient.MaxSeqResponse> response =
                    messageServiceClient.getMaxSeqByUserId(userId);

                if (response.isSuccess() && response.getData() != null) {
                    Long maxSeq = response.getData().getMaxSeq();
                    log.debug("查询用户最大序列号成功 - 用户ID: {}, 最大序列号: {}", userId, maxSeq);
                    return maxSeq != null ? maxSeq : 0L;
                } else {
                    log.error("查询用户最大序列号失败 - 用户ID: {}, 错误: {}", userId, response.getErrorMessage());
                    return 0L;
                }

            } else if (businessKey.startsWith("conversation_")) {
                String conversationId = businessKey.substring(13); // 去掉 "conversation_" 前缀
                ApiResponse<MessageServiceClient.MaxSeqResponse> response =
                    messageServiceClient.getMaxSeqByConversationId(conversationId);

                if (response.isSuccess() && response.getData() != null) {
                    Long maxSeq = response.getData().getMaxSeq();
                    log.debug("查询会话最大序列号成功 - 会话ID: {}, 最大序列号: {}", conversationId, maxSeq);
                    return maxSeq != null ? maxSeq : 0L;
                } else {
                    log.error("查询会话最大序列号失败 - 会话ID: {}, 错误: {}", conversationId, response.getErrorMessage());
                    return 0L;
                }

            } else {
                log.warn("无法识别的业务key格式: {}, 返回默认值0", businessKey);
                return 0L;
            }

        } catch (Exception e) {
            log.error("查询最大序列号失败 - 业务key: {}", businessKey, e);
            return 0L;
        }
    }

    /**
     * 健康检查
     * 使用 Feign 客户端替换原有的 HttpClient 实现
     * 保持相同的方法签名，体现 LSP 里氏替换原则
     *
     * @return 是否健康
     */
    public boolean isHealthy() {
        try {
            ApiResponse<MessageServiceClient.HealthResponse> response =
                messageServiceClient.healthCheck();

            if (response.isSuccess() && response.getData() != null) {
                boolean isHealthy = "UP".equals(response.getData().getStatus());
                log.debug("消息服务健康检查结果: {}", isHealthy ? "健康" : "不健康");
                return isHealthy;
            } else {
                log.warn("消息服务健康检查失败: {}", response.getErrorMessage());
                return false;
            }

        } catch (Exception e) {
            log.warn("消息服务健康检查失败", e);
            return false;
        }
    }
}
