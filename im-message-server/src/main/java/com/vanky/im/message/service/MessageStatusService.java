package com.vanky.im.message.service;

/**
 * 消息状态管理服务接口
 * 负责管理消息的推送状态和确认状态
 */
public interface MessageStatusService {

    /**
     * 更新消息推送状态为已送达（客户端已确认）
     * @param msgId 消息ID
     * @param seq 消息序列号
     * @param userId 确认用户ID
     * @return 更新是否成功
     */
    boolean updateMessageDelivered(String msgId, String seq, String userId);

    /**
     * 更新消息状态为已读
     * @param msgId 消息ID
     * @param userId 读取用户ID
     * @return 更新是否成功
     */
    boolean updateMessageRead(String msgId, String userId);

    /**
     * 更新消息状态为撤回
     * @param msgId 消息ID
     * @param userId 撤回用户ID
     * @return 更新是否成功
     */
    boolean updateMessageRecalled(String msgId, String userId);

    /**
     * 更新消息状态为推送失败
     * @param msgId 消息ID
     * @param reason 失败原因
     * @return 更新是否成功
     */
    boolean updateMessageFailed(String msgId, String reason);

    /**
     * 获取消息的推送状态
     * @param msgId 消息ID
     * @return 消息状态
     */
    int getMessageStatus(String msgId);

    /**
     * 检查消息是否已送达
     * @param msgId 消息ID
     * @return true-已送达，false-未送达
     */
    boolean isMessageDelivered(String msgId);
}
