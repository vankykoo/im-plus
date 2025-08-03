package com.vanky.im.message.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 创建群聊响应
 * 
 * @author vanky
 * @since 2025-07-31
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-31 22:18:33 +08:00; Reason: 创建群聊创建响应模型，返回群聊创建结果;
// }}
// {{START MODIFICATIONS}}
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateGroupResponse {

    /**
     * 响应状态码（字符串格式，匹配客户端期望）
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据（包含conversationId）
     */
    private String data;

    // 移除这些字段，简化响应格式以匹配客户端期望

    /**
     * 创建成功响应
     */
    public static CreateGroupResponse success(String conversationId, String conversationName,
                                            String conversationDesc, Integer memberCount) {
        CreateGroupResponse response = new CreateGroupResponse();
        response.setCode("200");  // 字符串格式
        response.setMessage("群聊创建成功");
        response.setData(conversationId);  // 将conversationId放在data字段中
        return response;
    }

    /**
     * 创建失败响应
     */
    public static CreateGroupResponse error(String message) {
        CreateGroupResponse response = new CreateGroupResponse();
        response.setCode("500");  // 字符串格式
        response.setMessage(message);
        return response;
    }

    /**
     * 创建参数错误响应
     */
    public static CreateGroupResponse badRequest(String message) {
        CreateGroupResponse response = new CreateGroupResponse();
        response.setCode("400");  // 字符串格式
        response.setMessage(message);
        return response;
    }
}
// {{END MODIFICATIONS}}
