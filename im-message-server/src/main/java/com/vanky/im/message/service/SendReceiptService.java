package com.vanky.im.message.service;

import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.message.service.GatewayMessagePushService;
import com.vanky.im.message.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息发送回执服务
 * 负责构建和发送消息发送回执给发送方客户端
 * 
 * @author vanky
 * @create 2025-08-05
 */
// [INTERNAL_ACTION: Fetching current time via mcp.time-mcp.]
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-05 11:57:30 +08:00; Reason: 创建消息发送回执服务，负责构建和发送回执消息;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class SendReceiptService {
    
    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;

    @Autowired
    private OnlineStatusService onlineStatusService;
    
    /**
     * 发送消息发送回执
     * @param clientSeq 客户端序列号
     * @param serverMsgId 服务端生成的消息ID
     * @param serverSeq 服务端生成的序列号
     * @param toUserId 接收回执的用户ID（消息发送方）
     */
    public void sendReceipt(String clientSeq, String serverMsgId, String serverSeq, String toUserId) {
        if (isEmptyOrNull(clientSeq)) {
            log.warn("发送回执失败，客户端序列号为空");
            return;
        }
        
        if (isEmptyOrNull(serverMsgId)) {
            log.warn("发送回执失败，服务端消息ID为空");
            return;
        }
        
        if (isEmptyOrNull(serverSeq)) {
            log.warn("发送回执失败，服务端序列号为空");
            return;
        }
        
        if (isEmptyOrNull(toUserId)) {
            log.warn("发送回执失败，目标用户ID为空");
            return;
        }
        
        try {
            // 构建回执消息
            ChatMessage receiptMessage = buildReceiptMessage(clientSeq, serverMsgId, serverSeq, toUserId);

            // 获取用户在线状态和网关ID
            UserSession userSession = onlineStatusService.getUserOnlineStatus(toUserId);
            if (userSession == null || userSession.getNodeId() == null) {
                log.warn("用户不在线或未找到网关信息，无法发送回执 - 用户: {}", toUserId);
                return;
            }
            String gatewayId = userSession.getNodeId();

            // 发送回执消息到指定网关
            gatewayMessagePushService.pushMessageToGateway(receiptMessage,
                    System.currentTimeMillis(), gatewayId, toUserId);

            log.info("发送回执成功 - 客户端序列号: {}, 服务端消息ID: {}, 服务端序列号: {}, 目标用户: {}, 网关: {}",
                    clientSeq, serverMsgId, serverSeq, toUserId, gatewayId);

        } catch (Exception e) {
            log.error("发送回执异常 - 客户端序列号: {}, 服务端消息ID: {}, 服务端序列号: {}, 目标用户: {}",
                    clientSeq, serverMsgId, serverSeq, toUserId, e);
        }
    }
    
    /**
     * 构建回执消息
     * @param clientSeq 客户端序列号
     * @param serverMsgId 服务端消息ID
     * @param serverSeq 服务端序列号
     * @param toUserId 目标用户ID
     * @return 回执消息
     */
    private ChatMessage buildReceiptMessage(String clientSeq, String serverMsgId, String serverSeq, String toUserId) {
        return ChatMessage.newBuilder()
                .setType(MessageTypeConstants.MESSAGE_SEND_RECEIPT)
                .setContent("消息发送回执")
                .setFromId("system")
                .setToId(toUserId)
                .setUid("receipt_" + System.currentTimeMillis()) // 回执消息的唯一ID
                .setSeq(String.valueOf(System.currentTimeMillis()))
                .setTimestamp(System.currentTimeMillis())
                .setRetry(0)
                .setClientSeq(clientSeq) // 客户端序列号，用于匹配
                .setServerMsgId(serverMsgId) // 服务端生成的消息ID
                .setServerSeq(serverSeq) // 服务端生成的序列号
                .build();
    }
    
    /**
     * 批量发送回执（用于批量消息处理场景）
     * @param receipts 回执信息列表
     */
    public void sendBatchReceipts(java.util.List<ReceiptInfo> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            log.warn("批量发送回执失败，回执列表为空");
            return;
        }
        
        log.info("开始批量发送回执，数量: {}", receipts.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (ReceiptInfo receipt : receipts) {
            try {
                sendReceipt(receipt.getClientSeq(), receipt.getServerMsgId(), 
                           receipt.getServerSeq(), receipt.getToUserId());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("批量发送回执失败 - 客户端序列号: {}, 目标用户: {}", 
                         receipt.getClientSeq(), receipt.getToUserId(), e);
            }
        }
        
        log.info("批量发送回执完成 - 总数: {}, 成功: {}, 失败: {}", 
                receipts.size(), successCount, failureCount);
    }
    
    /**
     * 验证回执参数
     * @param clientSeq 客户端序列号
     * @param serverMsgId 服务端消息ID
     * @param serverSeq 服务端序列号
     * @param toUserId 目标用户ID
     * @return 是否有效
     */
    public boolean isValidReceiptParams(String clientSeq, String serverMsgId, String serverSeq, String toUserId) {
        return !isEmptyOrNull(clientSeq) && 
               !isEmptyOrNull(serverMsgId) && 
               !isEmptyOrNull(serverSeq) && 
               !isEmptyOrNull(toUserId);
    }
    
    /**
     * 检查字符串是否为空或null
     * @param str 待检查的字符串
     * @return true-为空或null，false-有效
     */
    private boolean isEmptyOrNull(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    // ========== 内部数据类 ==========
    
    /**
     * 回执信息数据类
     */
    public static class ReceiptInfo {
        private String clientSeq;
        private String serverMsgId;
        private String serverSeq;
        private String toUserId;
        
        public ReceiptInfo() {}
        
        public ReceiptInfo(String clientSeq, String serverMsgId, String serverSeq, String toUserId) {
            this.clientSeq = clientSeq;
            this.serverMsgId = serverMsgId;
            this.serverSeq = serverSeq;
            this.toUserId = toUserId;
        }
        
        // Getter和Setter方法
        public String getClientSeq() {
            return clientSeq;
        }
        
        public void setClientSeq(String clientSeq) {
            this.clientSeq = clientSeq;
        }
        
        public String getServerMsgId() {
            return serverMsgId;
        }
        
        public void setServerMsgId(String serverMsgId) {
            this.serverMsgId = serverMsgId;
        }
        
        public String getServerSeq() {
            return serverSeq;
        }
        
        public void setServerSeq(String serverSeq) {
            this.serverSeq = serverSeq;
        }
        
        public String getToUserId() {
            return toUserId;
        }
        
        public void setToUserId(String toUserId) {
            this.toUserId = toUserId;
        }
        
        @Override
        public String toString() {
            return String.format("ReceiptInfo{clientSeq='%s', serverMsgId='%s', serverSeq='%s', toUserId='%s'}",
                    clientSeq, serverMsgId, serverSeq, toUserId);
        }
    }
}
// {{END MODIFICATIONS}}
