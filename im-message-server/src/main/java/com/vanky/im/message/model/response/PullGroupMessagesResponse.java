package com.vanky.im.message.model.response;

import com.vanky.im.message.model.MessageInfo;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 拉取群聊消息响应
 * 用于读扩散模式下的群聊消息同步
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 创建群聊消息拉取响应模型，支持读扩散模式的消息同步;
// }}
// {{START MODIFICATIONS}}
@Data
public class PullGroupMessagesResponse {
    
    /**
     * 按会话ID分组的消息列表
     * Key: conversation_id (例如: "group_101")
     * Value: 该会话的新消息列表，按seq升序排列
     * 
     * 例如：
     * {
     *   "group_101": [
     *     {msgId: "99001", seq: 481, content: "消息1", ...},
     *     {msgId: "99002", seq: 482, content: "消息2", ...}
     *   ],
     *   "group_102": []
     * }
     */
    private Map<String, List<MessageInfo>> conversations;
    
    /**
     * 每个会话的最新序列号
     * Key: conversation_id
     * Value: 该会话当前的最大seq
     * 
     * 客户端可以用这个值更新本地的last_read_seq
     */
    private Map<String, Long> latestSeqs;
    
    /**
     * 总拉取消息数量
     */
    private Integer totalCount;
    
    /**
     * 是否还有更多消息（分页标识）
     */
    private Boolean hasMore;
    
    /**
     * 响应时间戳
     */
    private Long timestamp;
    
    public PullGroupMessagesResponse() {
        this.timestamp = System.currentTimeMillis();
        this.totalCount = 0;
        this.hasMore = false;
    }
}
// {{END MODIFICATIONS}}
