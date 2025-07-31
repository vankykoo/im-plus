package com.vanky.im.message.model;

import lombok.Data;



/**
 * 离线消息同步检查请求
 * 用于客户端向服务端查询是否需要进行消息内容同步
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建离线消息同步检查请求模型，实现消息内容同步第二步功能;
// }}
// {{START MODIFICATIONS}}
@Data
public class SyncMessagesRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 客户端当前的全局同步点
     * 表示客户端已同步到的最新消息序列号
     */
    private Long lastSyncSeq;

    public SyncMessagesRequest() {
    }

    public SyncMessagesRequest(String userId, Long lastSyncSeq) {
        this.userId = userId;
        this.lastSyncSeq = lastSyncSeq;
    }

    @Override
    public String toString() {
        return "SyncMessagesRequest{" +
                "userId='" + userId + '\'' +
                ", lastSyncSeq=" + lastSyncSeq +
                '}';
    }
}
// {{END MODIFICATIONS}}
