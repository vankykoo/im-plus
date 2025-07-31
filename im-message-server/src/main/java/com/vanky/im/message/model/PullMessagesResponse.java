package com.vanky.im.message.model;

import lombok.Data;

import java.util.List;

/**
 * 批量拉取消息响应
 * 服务端向客户端返回一批离线消息内容
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建批量拉取消息响应模型，返回分页的离线消息内容;
// }}
// {{START MODIFICATIONS}}
@Data
public class PullMessagesResponse {

    /**
     * 消息列表
     */
    private List<MessageInfo> messages;

    /**
     * 是否还有更多消息
     * true - 还有更多消息，客户端应继续拉取
     * false - 已拉取完毕，同步结束
     */
    private boolean hasMore;

    /**
     * 下次拉取的起始序列号
     * 通常为本次返回的最后一条消息的seq + 1
     */
    private Long nextSeq;

    /**
     * 本次返回的消息数量
     */
    private Integer count;

    /**
     * 响应时间戳
     */
    private Long responseTime;

    /**
     * 错误消息（当处理失败时）
     */
    private String errorMessage;

    /**
     * 响应是否成功
     */
    private boolean success;

    public PullMessagesResponse() {
        this.responseTime = System.currentTimeMillis();
        this.success = true;
        this.hasMore = false;
        this.count = 0;
    }

    /**
     * 创建成功响应
     */
    public static PullMessagesResponse createSuccessResponse(List<MessageInfo> messages, boolean hasMore, Long nextSeq) {
        PullMessagesResponse response = new PullMessagesResponse();
        response.setMessages(messages);
        response.setHasMore(hasMore);
        response.setNextSeq(nextSeq);
        response.setCount(messages != null ? messages.size() : 0);
        return response;
    }

    /**
     * 创建空响应（无消息）
     */
    public static PullMessagesResponse createEmptyResponse(Long nextSeq) {
        PullMessagesResponse response = new PullMessagesResponse();
        response.setMessages(List.of());
        response.setHasMore(false);
        response.setNextSeq(nextSeq);
        response.setCount(0);
        return response;
    }

    /**
     * 创建错误响应
     */
    public static PullMessagesResponse createErrorResponse(String errorMessage) {
        PullMessagesResponse response = new PullMessagesResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setMessages(List.of());
        return response;
    }

    @Override
    public String toString() {
        return "PullMessagesResponse{" +
                "messageCount=" + count +
                ", hasMore=" + hasMore +
                ", nextSeq=" + nextSeq +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
// {{END MODIFICATIONS}}
