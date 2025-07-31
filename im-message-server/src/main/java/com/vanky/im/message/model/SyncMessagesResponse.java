package com.vanky.im.message.model;

import lombok.Data;

/**
 * 离线消息同步检查响应
 * 服务端向客户端返回同步指令，告知是否需要进行消息内容同步
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建离线消息同步检查响应模型，返回同步指令给客户端;
// }}
// {{START MODIFICATIONS}}
@Data
public class SyncMessagesResponse {

    /**
     * 是否需要同步
     * true - 存在离线消息，需要同步
     * false - 无离线消息，无需同步
     */
    private boolean syncNeeded;

    /**
     * 服务端当前最新的序列号
     * 即该用户在user_msg_list表中的最大seq值
     */
    private Long targetSeq;

    /**
     * 客户端上报的最后同步序列号
     */
    private Long currentSeq;

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

    public SyncMessagesResponse() {
        this.responseTime = System.currentTimeMillis();
        this.success = true;
    }

    /**
     * 创建需要同步的响应
     */
    public static SyncMessagesResponse createSyncNeededResponse(Long targetSeq, Long currentSeq) {
        SyncMessagesResponse response = new SyncMessagesResponse();
        response.setSyncNeeded(true);
        response.setTargetSeq(targetSeq);
        response.setCurrentSeq(currentSeq);
        return response;
    }

    /**
     * 创建无需同步的响应
     */
    public static SyncMessagesResponse createNoSyncResponse(Long targetSeq, Long currentSeq) {
        SyncMessagesResponse response = new SyncMessagesResponse();
        response.setSyncNeeded(false);
        response.setTargetSeq(targetSeq);
        response.setCurrentSeq(currentSeq);
        return response;
    }

    /**
     * 创建错误响应
     */
    public static SyncMessagesResponse createErrorResponse(String errorMessage) {
        SyncMessagesResponse response = new SyncMessagesResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setSyncNeeded(false);
        return response;
    }

    @Override
    public String toString() {
        return "SyncMessagesResponse{" +
                "syncNeeded=" + syncNeeded +
                ", targetSeq=" + targetSeq +
                ", currentSeq=" + currentSeq +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
// {{END MODIFICATIONS}}
