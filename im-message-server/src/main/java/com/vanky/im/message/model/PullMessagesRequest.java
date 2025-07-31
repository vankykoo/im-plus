package com.vanky.im.message.model;

import lombok.Data;



/**
 * 批量拉取消息请求
 * 客户端分页拉取离线消息内容时使用
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建批量拉取消息请求模型，支持分页拉取离线消息内容;
// }}
// {{START MODIFICATIONS}}
@Data
public class PullMessagesRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 起始序列号（包含）
     * 从该序列号开始拉取消息
     */
    private Long fromSeq;

    /**
     * 拉取数量限制
     * 默认200条，最大不超过500条
     */
    private Integer limit = 200;

    /**
     * 默认拉取数量
     */
    public static final int DEFAULT_LIMIT = 200;

    /**
     * 最大拉取数量
     */
    public static final int MAX_LIMIT = 500;

    public PullMessagesRequest() {
    }

    public PullMessagesRequest(String userId, Long fromSeq) {
        this.userId = userId;
        this.fromSeq = fromSeq;
        this.limit = DEFAULT_LIMIT;
    }

    public PullMessagesRequest(String userId, Long fromSeq, Integer limit) {
        this.userId = userId;
        this.fromSeq = fromSeq;
        this.limit = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    }

    /**
     * 设置拉取数量限制，自动校验范围
     */
    public void setLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            this.limit = DEFAULT_LIMIT;
        } else {
            this.limit = Math.min(limit, MAX_LIMIT);
        }
    }

    @Override
    public String toString() {
        return "PullMessagesRequest{" +
                "userId='" + userId + '\'' +
                ", fromSeq=" + fromSeq +
                ", limit=" + limit +
                '}';
    }
}
// {{END MODIFICATIONS}}
