package com.vanky.im.message.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建群聊请求
 * 
 * @author vanky
 * @since 2025-07-31
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-31 22:18:33 +08:00; Reason: 创建群聊创建请求模型，支持客户端创建群聊功能;
// }}
// {{START MODIFICATIONS}}
@Data
public class CreateGroupRequest {

    /**
     * 群聊名称
     */
    @NotBlank(message = "群聊名称不能为空")
    @Size(max = 50, message = "群聊名称不能超过50个字符")
    private String conversationName;

    /**
     * 群聊描述
     */
    @Size(max = 200, message = "群聊描述不能超过200个字符")
    private String conversationDesc;

    /**
     * 会话类型（固定为GROUP）
     */
    @NotBlank(message = "会话类型不能为空")
    private String conversationType;

    /**
     * 创建者ID
     */
    @NotBlank(message = "创建者ID不能为空")
    private String creatorId;

    /**
     * 群聊成员列表（包含创建者）
     */
    @NotNull(message = "群聊成员列表不能为空")
    @Size(min = 2, message = "群聊至少需要2个成员")
    private List<String> members;

    /**
     * 验证会话类型是否为群聊
     */
    public boolean isGroupConversation() {
        return "GROUP".equalsIgnoreCase(conversationType);
    }

    /**
     * 获取成员数量
     */
    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
}
// {{END MODIFICATIONS}}
