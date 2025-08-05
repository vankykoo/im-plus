package com.vanky.im.testclient.model;

/**
 * 消息状态枚举
 * 定义消息在发送过程中的各种状态
 * 
 * @author vanky
 * @create 2025-08-05
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-05 11:49:30 +08:00; Reason: 创建消息状态枚举，支持消息状态管理;
// }}
// {{START MODIFICATIONS}}
public enum MessageStatus {
    
    /**
     * 发送中 - 消息已发送，等待服务端回执
     */
    SENDING("发送中"),
    
    /**
     * 已送达 - 收到服务端回执，消息已被服务端处理
     */
    DELIVERED("已送达"),
    
    /**
     * 发送失败 - 超时重试后仍未收到回执，标记为失败
     */
    FAILED("发送失败");
    
    private final String description;
    
    MessageStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
// {{END MODIFICATIONS}}
