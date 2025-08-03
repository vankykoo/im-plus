package com.vanky.im.message.model.request;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * 拉取群聊消息请求
 * 用于读扩散模式下的群聊消息同步
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 创建群聊消息拉取请求模型，支持读扩散模式的消息同步;
// }}
// {{START MODIFICATIONS}}
@Data
public class PullGroupMessagesRequest {
    
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private String userId;
    
    /**
     * 群聊会话状态映射
     * Key: conversation_id (例如: "group_101")
     * Value: last_read_seq (用户在该群聊中已读的最大序列号)
     * 
     * 例如：
     * {
     *   "group_101": 480,
     *   "group_102": 1250
     * }
     */
    @NotEmpty(message = "会话状态不能为空")
    private Map<String, Long> conversations;
    
    /**
     * 每个会话最大拉取消息数量（可选，默认100）
     */
    private Integer limit = 100;
    
    /**
     * 是否只拉取消息ID列表（可选，默认false，返回完整消息内容）
     */
    private Boolean onlyIds = false;
}
// {{END MODIFICATIONS}}
