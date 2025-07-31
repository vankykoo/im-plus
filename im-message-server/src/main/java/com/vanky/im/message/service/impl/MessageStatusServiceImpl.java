package com.vanky.im.message.service.impl;

import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.service.MessageStatusService;
import com.vanky.im.message.service.PrivateMessageService;
import com.vanky.im.message.service.GroupMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息状态管理服务实现类
 */
@Slf4j
@Service
public class MessageStatusServiceImpl implements MessageStatusService {

    @Autowired
    private PrivateMessageService privateMessageService;

    @Autowired
    private GroupMessageService groupMessageService;

    @Autowired
    private MessageService messageService;

    @Override
    public boolean updateMessageDelivered(String msgId, String seq, String userId) {
        try {
            log.info("更新消息推送状态为已送达 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);

            // 使用统一的MessageService更新消息状态
            boolean updated = messageService.updateMessageStatus(msgId, (int)MessageConstants.MESSAGE_STATUS_DELIVERED);
            if (updated) {
                log.debug("消息状态更新成功 - 消息ID: {}", msgId);
                return true;
            }

            log.warn("未找到对应的消息记录 - 消息ID: {}, 序列号: {}", msgId, seq);
            return false;

        } catch (Exception e) {
            log.error("更新消息推送状态失败 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId, e);
            return false;
        }
    }

    @Override
    public int batchUpdateMessageDelivered(String[] msgIds, String userId) {
        if (msgIds == null || msgIds.length == 0) {
            log.warn("批量更新消息状态失败 - 消息ID列表为空, 用户: {}", userId);
            return 0;
        }

        try {
            log.info("批量更新消息推送状态为已送达 - 用户: {}, 消息数量: {}", userId, msgIds.length);

            int successCount = 0;
            for (String msgId : msgIds) {
                if (msgId != null && !msgId.trim().isEmpty()) {
                    // 使用统一的MessageService更新消息状态
                    boolean updated = messageService.updateMessageStatus(msgId.trim(),
                            (int)MessageConstants.MESSAGE_STATUS_DELIVERED);
                    if (updated) {
                        successCount++;
                        log.debug("消息状态更新成功 - 消息ID: {}", msgId.trim());
                    } else {
                        log.warn("未找到对应的消息记录 - 消息ID: {}", msgId.trim());
                    }
                }
            }

            log.info("批量更新消息推送状态完成 - 用户: {}, 总数: {}, 成功: {}",
                    userId, msgIds.length, successCount);
            return successCount;

        } catch (Exception e) {
            log.error("批量更新消息推送状态失败 - 用户: {}, 消息数量: {}", userId, msgIds.length, e);
            return 0;
        }
    }

    @Override
    public boolean updateMessageRead(String msgId, String userId) {
        try {
            log.info("更新消息状态为已读 - 消息ID: {}, 用户: {}", msgId, userId);

            // 使用统一的MessageService更新消息状态
            return messageService.updateMessageStatus(msgId, (int)MessageConstants.MESSAGE_STATUS_READ);

        } catch (Exception e) {
            log.error("更新消息已读状态失败 - 消息ID: {}, 用户: {}", msgId, userId, e);
            return false;
        }
    }

    @Override
    public boolean updateMessageRecalled(String msgId, String userId) {
        try {
            log.info("更新消息状态为撤回 - 消息ID: {}, 用户: {}", msgId, userId);

            // 使用统一的MessageService更新消息状态
            return messageService.updateMessageStatus(msgId, (int)MessageConstants.MESSAGE_STATUS_RECALLED);

        } catch (Exception e) {
            log.error("更新消息撤回状态失败 - 消息ID: {}, 用户: {}", msgId, userId, e);
            return false;
        }
    }

    @Override
    public boolean updateMessageFailed(String msgId, String reason) {
        try {
            log.info("更新消息状态为推送失败 - 消息ID: {}, 原因: {}", msgId, reason);

            // 使用统一的MessageService更新消息状态
            return messageService.updateMessageStatus(msgId, (int)MessageConstants.MESSAGE_STATUS_FAILED);

        } catch (Exception e) {
            log.error("更新消息失败状态失败 - 消息ID: {}, 原因: {}", msgId, reason, e);
            return false;
        }
    }

    @Override
    public int getMessageStatus(String msgId) {
        try {
            // 使用统一的MessageService查询消息状态
            Message message = messageService.getByMsgId(msgId);
            if (message != null) {
                return message.getStatus();
            }

            log.warn("未找到消息记录 - 消息ID: {}", msgId);
            return -1; // 表示消息不存在

        } catch (Exception e) {
            log.error("获取消息状态失败 - 消息ID: {}", msgId, e);
            return -1;
        }
    }

    @Override
    public boolean isMessageDelivered(String msgId) {
        int status = getMessageStatus(msgId);
        return status == MessageConstants.MESSAGE_STATUS_DELIVERED || 
               status == MessageConstants.MESSAGE_STATUS_READ;
    }

    /**
     * 更新私聊消息状态
     */
    private boolean updatePrivateMessageStatus(String msgId, int status) {
        try {
            var privateMessage = privateMessageService.getByMsgId(msgId);
            if (privateMessage != null) {
                privateMessage.setStatus(status);
                return privateMessageService.updateById(privateMessage);
            }
            return false;
        } catch (Exception e) {
            log.error("更新私聊消息状态失败 - 消息ID: {}, 状态: {}", msgId, status, e);
            return false;
        }
    }

    /**
     * 更新群聊消息状态
     */
    private boolean updateGroupMessageStatus(String msgId, int status) {
        try {
            var groupMessage = groupMessageService.getByMsgId(msgId);
            if (groupMessage != null) {
                groupMessage.setStatus(status);
                return groupMessageService.updateById(groupMessage);
            }
            return false;
        } catch (Exception e) {
            log.error("更新群聊消息状态失败 - 消息ID: {}, 状态: {}", msgId, status, e);
            return false;
        }
    }
}
