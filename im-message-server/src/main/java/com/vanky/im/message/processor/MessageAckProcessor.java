package com.vanky.im.message.processor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.message.service.MessageStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消息确认处理器
 * 处理客户端发送的ACK确认消息，更新消息推送状态
 */
@Slf4j
@Component
public class MessageAckProcessor {

    @Autowired
    private MessageStatusService messageStatusService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理消息确认
     * @param chatMessage ACK确认消息
     */
    public void processMessageAck(ChatMessage chatMessage) {
        String msgId = chatMessage.getUid();
        String seq = chatMessage.getSeq();
        String userId = chatMessage.getFromId();
        
        log.info("开始处理消息确认 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
        
        try {
            // 验证ACK消息的有效性
            if (!validateAckMessage(chatMessage)) {
                log.warn("ACK消息验证失败 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
                return;
            }
            
            // 更新消息状态为已送达
            boolean updated = messageStatusService.updateMessageDelivered(msgId, seq, userId);
            
            if (updated) {
                log.info("消息确认处理成功 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
            } else {
                log.warn("消息确认处理失败，未找到对应消息 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);
            }
            
        } catch (Exception e) {
            log.error("处理消息确认异常 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId, e);
        }
    }

    /**
     * 处理批量消息确认
     * @param chatMessage 批量ACK确认消息，消息内容包含多个消息ID，用逗号分隔
     */
    public void processBatchMessageAck(ChatMessage chatMessage) {
        String userId = chatMessage.getFromId();
        String msgIdsStr = chatMessage.getContent(); // 消息内容包含多个消息ID，用逗号分隔

        log.info("开始处理批量消息确认 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr);

        try {
            // 验证批量ACK消息的有效性
            if (!validateBatchAckMessage(chatMessage)) {
                log.warn("批量ACK消息验证失败 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr);
                return;
            }

            // 解析消息ID列表
            String[] msgIds = msgIdsStr.split(",");
            if (msgIds.length == 0) {
                log.warn("批量ACK消息ID列表为空 - 用户: {}", userId);
                return;
            }

            // 批量更新消息状态为已送达
            int updatedCount = messageStatusService.batchUpdateMessageDelivered(msgIds, userId);

            log.info("批量消息确认处理完成 - 用户: {}, 总消息数: {}, 成功更新: {}",
                    userId, msgIds.length, updatedCount);

        } catch (Exception e) {
            log.error("处理批量消息确认异常 - 用户: {}, 消息ID列表: {}", userId, msgIdsStr, e);
        }
    }

    /**
     * 处理群聊会话ACK确认
     * @param chatMessage 群聊会话ACK消息，消息内容格式：conversationId1:seq1,conversationId2:seq2
     */
    public void processGroupConversationAck(ChatMessage chatMessage) {
        String userId = chatMessage.getFromId();
        String content = chatMessage.getContent(); // conversationId1:seq1,conversationId2:seq2

        log.info("开始处理群聊会话ACK确认 - 用户: {}, 内容: {}", userId, content);

        try {
            // 验证群聊ACK消息的有效性
            if (!validateGroupConversationAckMessage(chatMessage)) {
                log.warn("群聊会话ACK消息验证失败 - 用户: {}, 内容: {}", userId, content);
                return;
            }

            // 解析会话ID和seq映射
            Map<String, Long> conversationSeqs = parseConversationSeqs(content);
            if (conversationSeqs.isEmpty()) {
                log.warn("群聊会话ACK内容解析为空 - 用户: {}, 内容: {}", userId, content);
                return;
            }

            // 更新Redis中的用户群聊同步点
            updateUserConversationSeqs(userId, conversationSeqs);

            log.info("群聊会话ACK确认处理完成 - 用户: {}, 更新会话数: {}", userId, conversationSeqs.size());

        } catch (Exception e) {
            log.error("处理群聊会话ACK确认异常 - 用户: {}, 内容: {}", userId, content, e);
        }
    }

    /**
     * 验证ACK消息的有效性
     * @param chatMessage ACK消息
     * @return 验证结果
     */
    private boolean validateAckMessage(ChatMessage chatMessage) {
        // 检查必要字段
        if (chatMessage.getUid() == null || chatMessage.getUid().isEmpty()) {
            log.warn("ACK消息缺少消息ID");
            return false;
        }
        
        if (chatMessage.getSeq() == null || chatMessage.getSeq().isEmpty()) {
            log.warn("ACK消息缺少序列号");
            return false;
        }
        
        if (chatMessage.getFromId() == null || chatMessage.getFromId().isEmpty()) {
            log.warn("ACK消息缺少用户ID");
            return false;
        }
        
        // 检查消息类型
        if (chatMessage.getType() != MessageTypeConstants.MESSAGE_ACK) {
            log.warn("ACK消息类型不正确 - 期望: {}, 实际: {}",
                    MessageTypeConstants.MESSAGE_ACK,
                    chatMessage.getType());
            return false;
        }
        
        return true;
    }

    /**
     * 验证批量ACK消息的有效性
     * @param chatMessage 批量ACK消息
     * @return 是否有效
     */
    private boolean validateBatchAckMessage(ChatMessage chatMessage) {
        // 检查用户ID
        if (chatMessage.getFromId() == null || chatMessage.getFromId().trim().isEmpty()) {
            log.warn("批量ACK消息缺少用户ID");
            return false;
        }

        // 检查消息内容（消息ID列表）
        if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
            log.warn("批量ACK消息缺少消息ID列表");
            return false;
        }

        // 检查消息类型
        if (chatMessage.getType() != MessageTypeConstants.BATCH_MESSAGE_ACK) {
            log.warn("批量ACK消息类型不正确: {}", chatMessage.getType());
            return false;
        }

        return true;
    }

    /**
     * 验证群聊会话ACK消息的有效性
     * @param chatMessage 群聊会话ACK消息
     * @return 是否有效
     */
    private boolean validateGroupConversationAckMessage(ChatMessage chatMessage) {
        // 检查用户ID
        if (chatMessage.getFromId() == null || chatMessage.getFromId().trim().isEmpty()) {
            log.warn("群聊会话ACK消息缺少用户ID");
            return false;
        }

        // 检查消息内容（会话ID:seq列表）
        if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
            log.warn("群聊会话ACK消息缺少会话seq列表");
            return false;
        }

        // 检查消息类型
        if (chatMessage.getType() != MessageTypeConstants.GROUP_CONVERSATION_ACK) {
            log.warn("群聊会话ACK消息类型不正确: {}", chatMessage.getType());
            return false;
        }

        return true;
    }

    /**
     * 解析群聊会话ACK内容，提取会话ID和seq映射
     * @param content 格式：conversationId1:seq1,conversationId2:seq2
     * @return 会话ID到seq的映射
     */
    private Map<String, Long> parseConversationSeqs(String content) {
        Map<String, Long> conversationSeqs = new HashMap<>();

        try {
            if (content == null || content.trim().isEmpty()) {
                return conversationSeqs;
            }

            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    String conversationId = parts[0].trim();
                    String seqStr = parts[1].trim();

                    if (!conversationId.isEmpty() && !seqStr.isEmpty()) {
                        try {
                            Long seq = Long.parseLong(seqStr);
                            conversationSeqs.put(conversationId, seq);
                            log.debug("解析群聊会话seq - 会话ID: {}, seq: {}", conversationId, seq);
                        } catch (NumberFormatException e) {
                            log.warn("解析seq失败 - 会话ID: {}, seq字符串: {}", conversationId, seqStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析群聊会话ACK内容异常 - 内容: {}", content, e);
        }

        return conversationSeqs;
    }

    /**
     * 更新Redis中的用户群聊同步点（会话级最大seq）
     * 只有当新的seq大于当前已存储的seq时才更新，确保seq单调递增
     * @param userId 用户ID
     * @param conversationSeqs 会话ID到seq的映射
     */
    private void updateUserConversationSeqs(String userId, Map<String, Long> conversationSeqs) {
        if (userId == null || conversationSeqs == null || conversationSeqs.isEmpty()) {
            log.warn("更新用户群聊同步点失败 - 参数为空: userId={}, conversationSeqs={}", userId, conversationSeqs);
            return;
        }

        try {
            String redisKey = RedisKeyConstants.getUserConversationSeqKey(userId);
            Map<String, String> fieldToValueMap = new HashMap<>();

            // 先获取当前已存储的seq值，确保只有更大的seq才会被更新
            for (Map.Entry<String, Long> entry : conversationSeqs.entrySet()) {
                String conversationId = entry.getKey();
                Long newSeq = entry.getValue();

                if (newSeq != null) {
                    // 获取当前已存储的seq
                    Object currentSeqObj = redisTemplate.opsForHash().get(redisKey, conversationId);
                    Long currentSeq = 0L;

                    if (currentSeqObj instanceof Long) {
                        currentSeq = (Long) currentSeqObj;
                    } else if (currentSeqObj instanceof Integer) {
                        currentSeq = ((Integer) currentSeqObj).longValue();
                    } else if (currentSeqObj instanceof String) {
                        try {
                            currentSeq = Long.parseLong((String) currentSeqObj);
                        } catch (NumberFormatException e) {
                            log.warn("解析当前seq失败，使用默认值0 - 会话ID: {}, 当前值: {}", conversationId, currentSeqObj);
                            currentSeq = 0L;
                        }
                    }

                    // 只有新seq大于当前seq时才更新
                    if (newSeq > currentSeq) {
                        fieldToValueMap.put(conversationId, newSeq.toString()); // 转换为字符串存储，兼容StringRedisSerializer
                        log.info("更新用户会话最大seq - 用户ID: {}, 会话ID: {}, 当前seq: {} -> 新seq: {}",
                                userId, conversationId, currentSeq, newSeq);
                    } else {
                        log.debug("跳过seq更新（新seq不大于当前seq） - 用户ID: {}, 会话ID: {}, 当前seq: {}, 新seq: {}",
                                userId, conversationId, currentSeq, newSeq);
                    }
                }
            }

            if (!fieldToValueMap.isEmpty()) {
                // 批量更新Redis Hash字段
                redisTemplate.opsForHash().putAll(redisKey, fieldToValueMap);

                // 设置过期时间（30天）
                redisTemplate.expire(redisKey, RedisKeyConstants.CONVERSATION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);

                log.info("成功更新用户群聊同步点 - 用户ID: {}, 更新数量: {}, Redis key: {}",
                        userId, fieldToValueMap.size(), redisKey);

                // 验证更新结果
                for (String conversationId : fieldToValueMap.keySet()) {
                    Object storedValue = redisTemplate.opsForHash().get(redisKey, conversationId);
                    log.debug("验证Redis更新结果 - 会话ID: {}, 存储值: {}", conversationId, storedValue);
                }
            } else {
                log.warn("没有有效的群聊同步点需要更新 - 用户ID: {}", userId);
            }
        } catch (Exception e) {
            log.error("更新用户群聊同步点异常 - 用户ID: {}, 错误: {}", userId, e.getMessage(), e);
        }
    }
}
