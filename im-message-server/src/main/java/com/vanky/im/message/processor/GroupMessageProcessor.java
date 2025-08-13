package com.vanky.im.message.processor;

import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.MessageConstants;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import com.vanky.im.message.client.SequenceClient;
import com.vanky.im.common.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 群聊消息处理器
 * 实现读扩散模式的消息存储逻辑
 */
@Component
public class GroupMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(GroupMessageProcessor.class);

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private GroupMessageService groupMessageService;

    @Autowired
    private MessageService messageService;
    
    @Autowired
    private ConversationMsgListService conversationMsgListService;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private GroupMemberService groupMemberService;
    
    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;

    @Autowired
    private MessageReceiverService messageReceiverService;

    @Autowired
    private GroupNotificationService groupNotificationService;

    @Autowired
    private GroupConversationUpdateService groupConversationUpdateService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private MessageIdempotentService messageIdempotentService;

    @Autowired
    private SequenceClient sequenceClient;

    @Autowired
    private MessageSendReceiptService messageSendReceiptService;

    // 雪花算法ID生成器
    private final SnowflakeIdGenerator snowflakeIdGenerator = SnowflakeIdGenerator.getInstance();

    // 消息缓存TTL（24小时）
    private static final long MESSAGE_CACHE_TTL = 24 * 60 * 60;
    
    // 用户消息列表缓存的最大数量
    private static final int USER_MSG_CACHE_MAX_SIZE = 100;

    /**
     * 处理群聊消息的读扩散存储
     * @param chatMessage 原始消息
     * @param conversationId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void processGroupMessage(ChatMessage chatMessage, String conversationId) {
        try {
            String fromUserId = chatMessage.getFromId();
            String groupId = chatMessage.getToId();
            String clientSeq = chatMessage.getClientSeq();

            log.info("开始处理群聊消息 - 会话ID: {}, 发送方: {}, 群组ID: {}, 客户端序列号: {}",
                    conversationId, fromUserId, groupId, clientSeq);

            // 0. 幂等性检查（仅对包含client_seq的消息进行检查）
            if (clientSeq != null && !clientSeq.trim().isEmpty()) {
                MessageIdempotentService.IdempotentResult idempotentResult =
                        messageIdempotentService.checkIdempotent(clientSeq);

                if (idempotentResult != null) {
                    // 重复消息，直接忽略（统一推送理念：发送方通过消息拉取补偿获取消息，不主动推送）
                    log.info("检测到重复群聊消息，直接忽略 - 客户端序列号: {}, 消息ID: {}, 序列号: {}",
                            clientSeq, idempotentResult.getMsgId(), idempotentResult.getSeq());

                    // 不做任何推送，发送方如果需要确认，通过消息拉取补偿机制获取
                    return;
                }
            }
            
            // 2. 校验发送者是否为群成员
            if (!groupMemberService.isGroupMember(groupId, fromUserId)) {
                log.warn("非群成员发送消息 - 用户ID: {}, 群组ID: {}", fromUserId, groupId);
                return;
            }
            
            // 3. 业务校验通过后，生成全局唯一的消息ID
            String msgId = snowflakeIdGenerator.nextIdString();
            log.info("生成消息ID - 会话ID: {}, 消息ID: {}, 发送方: {}", conversationId, msgId, fromUserId);
            
            // 构建包含新消息ID的ChatMessage
            chatMessage = ChatMessage.newBuilder(chatMessage)
                    .setUid(msgId)
                    .build();
            
            // 4. 获取群成员数量
            List<String> groupMembers = groupMemberService.getGroupMemberIds(groupId);
            int memberCount = groupMembers.size();
            
            // 5. 处理群聊会话信息（创建或更新）
            conversationService.handleGroupConversation(conversationId, fromUserId, groupId, memberCount);
            log.debug("群聊会话信息处理完成 - 会话ID: {}, 群组ID: {}, 成员数: {}", 
                      conversationId, groupId, memberCount);
            
            // 6. 使用新的序列号服务生成会话级序列号
            Long seq = sequenceClient.getNextSequence(conversationId);
            if (seq == null) {
                log.error("序列号服务生成失败 - 会话ID: {}", conversationId);
                throw new RuntimeException("序列号服务生成会话序列号失败");
            }
            log.debug("生成会话序列号 - 会话ID: {}, Seq: {}", conversationId, seq);
            
            // 7. 数据入库（读扩散模式）
            saveMessageData(chatMessage, msgId, conversationId, seq);
            
            // 8. 更新缓存
            updateCache(chatMessage, msgId, conversationId, seq);

            // 8. 简化更新会话视图（读扩散模式）
            updateGroupConversationView(chatMessage, groupId, msgId);

            // 9. 推送轻量级通知给在线群成员（读扩散模式）
            pushNotificationToOnlineMembers(chatMessage, groupId, seq, msgId);

            // 10. 更新发送方的会话级seq（发送成功后更新）
            updateSenderConversationSeq(fromUserId, conversationId, seq);

            // 11. 记录幂等性结果（仅对包含client_seq的消息）
            if (clientSeq != null && !clientSeq.trim().isEmpty()) {
                messageIdempotentService.recordIdempotent(clientSeq, msgId, seq);
            }

            // 12. 发送消息发送确认回执给发送方（事务提交后异步执行）
            sendGroupReceiptToSenderAsync(chatMessage, msgId, seq);

            log.info("群聊消息处理完成 - 会话ID: {}, 消息ID: {}, Seq: {}", conversationId, msgId, seq);
            
        } catch (Exception e) {
            log.error("处理群聊消息失败 - 发送方: {}, 群组ID: {}, 原始消息ID: {}", 
                    chatMessage.getFromId(), chatMessage.getToId(), chatMessage.getUid(), e);
            throw e;
        }
    }
    
    /**
     * 保存消息数据到数据库（纯读扩散模式）
     * 群聊采用纯读扩散模式，只保存消息内容和会话索引，不为每个用户创建记录
     *
     * @param chatMessage 原始消息
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param seq 序列号
     */
    private void saveMessageData(ChatMessage chatMessage, String msgId, String conversationId, Long seq) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 改造为纯读扩散模式，移除写扩散逻辑，只保存消息内容和会话索引;
        // }}
        // {{START MODIFICATIONS}}
        // 1. 保存消息主体到统一的message表（读扩散：只保存一份消息内容）
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_GROUP);
        message.setStatus(MessageConstants.MESSAGE_STATUS_SENT); // 初始状态为已发送，等待客户端确认
        messageService.save(message);
        log.debug("保存群聊消息主体完成 - 消息ID: {}, 消息类型: {}", msgId, MessageTypeConstants.MSG_TYPE_GROUP);

        // 2. 保存一条会话消息记录到conversation_msg_list表（读扩散：群公告栏，只插入一条记录）
        ConversationMsgList conversationMsgList = new ConversationMsgList();
        conversationMsgList.setConversationId(conversationId);
        conversationMsgList.setMsgId(Long.valueOf(msgId)); // 直接使用雪花算法生成的ID
        conversationMsgList.setSeq(seq);
        conversationMsgListService.save(conversationMsgList);
        log.debug("保存会话消息记录完成 - 会话ID: {}, Seq: {}", conversationId, seq);

        // 3. 读扩散模式：不再为每个群成员创建user_msg_list记录
        // 群聊消息的读取将通过conversation_msg_list表进行，用户主动拉取
        String groupId = chatMessage.getToId();
        List<String> groupMemberIds = groupMemberService.getGroupMemberIds(groupId);
        log.info("群聊消息存储完成（读扩散模式） - 群组ID: {}, 成员数量: {}, 写入成本: O(1)",
                groupId, groupMemberIds.size());
        // {{END MODIFICATIONS}}
    }
    
    /**
     * 更新缓存
     * 缓存消息内容到Redis，便于快速获取
     * 
     * @param chatMessage 原始消息
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param seq 序列号
     */
    private void updateCache(ChatMessage chatMessage, String msgId, String conversationId, Long seq) {
        // 1. 将新消息缓存到Redis (String, msgId -> message_json, TTL 1天)
        Message message = MessageConverter.convertToMessage(chatMessage, msgId, conversationId, MessageTypeConstants.MSG_TYPE_GROUP);
        String messageJson = MessageConverter.toJson(message);
        redisService.cacheMessage(msgId, messageJson, MESSAGE_CACHE_TTL);
        
        // 2. 为消息发送者添加消息索引到缓存 (ZSet, user:msg:list:{userId} -> {msgId, seq})
        redisService.addToUserMsgList(chatMessage.getFromId(), msgId, seq, USER_MSG_CACHE_MAX_SIZE);
        
        log.debug("缓存更新完成 - 消息ID: {}, 群组ID: {}", msgId, chatMessage.getToId());
    }
    
    /**
     * 为特定在线用户缓存消息
     * 这是读扩散模式的优化，为活跃用户提前缓存消息索引
     * 
     * @param userId 用户ID
     * @param msgId 消息ID
     * @param seq 序列号
     */
    private void cacheMessageForUser(String userId, String msgId, Long seq) {
        try {
            // 检查用户是否在线
            String userSessionKey = SessionConstants.getUserSessionKey(userId);
            Boolean isOnline = redisTemplate.hasKey(userSessionKey);
            
            // 如果用户在线，为其缓存消息
            if (Boolean.TRUE.equals(isOnline)) {
                redisService.addToUserMsgList(userId, msgId, seq, USER_MSG_CACHE_MAX_SIZE);
                log.debug("为在线用户缓存消息 - 用户ID: {}, 消息ID: {}, Seq: {}", userId, msgId, seq);
            }
        } catch (Exception e) {
            // 缓存错误不影响主要流程
            log.warn("为用户缓存消息失败 - 用户ID: {}, 消息ID: {}", userId, msgId, e);
        }
    }
    
    /**
     * 简化更新群聊会话视图（读扩散模式）
     * 只更新last_update_time，不计算unread_count
     *
     * @param chatMessage 原始消息
     * @param groupId 群组ID
     * @param msgId 消息ID
     */
    private void updateGroupConversationView(ChatMessage chatMessage, String groupId, String msgId) {
        try {
            // {{CHENGQI:
            // Action: Added; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 添加简化的群聊会话视图更新，支持读扩散模式;
            // }}
            // {{START MODIFICATIONS}}
            // 获取群成员列表
            List<String> groupMemberIds = groupMemberService.getGroupMemberIds(groupId);

            // 简化更新群聊会话视图
            groupConversationUpdateService.updateGroupConversationView(
                    "group_" + groupId, groupMemberIds, msgId);

            log.debug("群聊会话视图更新完成 - 群组ID: {}, 成员数: {}", groupId, groupMemberIds.size());
            // {{END MODIFICATIONS}}

        } catch (Exception e) {
            log.error("更新群聊会话视图失败 - 群组ID: {}", groupId, e);
            // 不抛出异常，不影响主流程
        }
    }

    /**
     * 推送轻量级通知给在线群成员（读扩散模式）
     * 不推送完整消息，只推送通知让客户端主动拉取
     *
     * @param chatMessage 原始消息
     * @param groupId 群组ID
     * @param seq 序列号
     * @param msgId 消息ID
     */
    private void pushNotificationToOnlineMembers(ChatMessage chatMessage, String groupId, Long seq, String msgId) {
        try {
            // {{CHENGQI:
            // Action: Modified; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 改为推送轻量级通知，支持读扩散模式;
            // }}
            // {{START MODIFICATIONS}}
            // 获取在线群成员及其网关信息
            Set<String> onlineMembers = groupMemberService.getOnlineGroupMembers(groupId);
            log.debug("准备推送群聊通知给在线成员 - 群组ID: {}, 在线成员数: {}", groupId, onlineMembers.size());

            // 构建在线成员到网关的映射
            java.util.Map<String, String> memberToGatewayMap = new java.util.HashMap<>();

            for (String memberId : onlineMembers) {
                try {
                    // 统一推送逻辑：发送方也接收自己的消息作为发送确认
                    // 移除跳过发送者的逻辑，让发送方也能收到群聊消息

                    // 获取成员的会话信息
                    String userSessionKey = SessionConstants.getUserSessionKey(memberId);
                    UserSession userSession = (UserSession) redisTemplate.opsForValue().get(userSessionKey);

                    if (userSession != null && userSession.getNodeId() != null) {
                        memberToGatewayMap.put(memberId, userSession.getNodeId());

                        // 为活跃用户添加消息缓存
                        cacheMessageForUser(memberId, msgId, seq);

                        // 群聊特殊处理：发送方作为群成员也需要收到完整消息
                        // 注意：发送方会收到两种消息：
                        // 1. 完整的群聊消息（作为群成员，需要在群聊界面显示）
                        // 2. MESSAGE_SEND_RECEIPT回执（确认消息发送状态）
                        if (memberId.equals(chatMessage.getFromId())) {
                            log.debug("群聊消息将推送给发送方 - 发送方: {}, 群组: {} (发送方作为群成员需要看到消息)", memberId, groupId);
                        }
                    }
                } catch (Exception e) {
                    log.error("获取成员会话信息失败 - 成员ID: {}", memberId, e);
                }
            }

            // 批量推送轻量级通知
            if (!memberToGatewayMap.isEmpty()) {
                // {{CHENGQI:
                // Action: Modified; Timestamp: 2025-08-04 21:00:00 +08:00; Reason: 修正群聊通知推送调用，传递会话级seq;
                // }}
                // {{START MODIFICATIONS}}
                groupNotificationService.pushNotificationToOnlineMembers(chatMessage, seq, memberToGatewayMap);

                log.info("群聊通知推送完成 - 群组ID: {}, 会话seq: {}, 通知成员数: {}", groupId, seq, memberToGatewayMap.size());
                // {{END MODIFICATIONS}}
            }
            // {{END MODIFICATIONS}}

        } catch (Exception e) {
            log.error("推送群聊通知失败 - 群组ID: {}", groupId, e);
            // 不抛出异常，不影响主流程
        }
    }

    /**
     * 更新发送方的会话级seq
     * 发送群聊消息成功后，需要更新发送方在该会话中的最大seq
     *
     * @param senderId 发送方用户ID
     * @param conversationId 会话ID
     * @param seq 会话级序列号
     */
    private void updateSenderConversationSeq(String senderId, String conversationId, Long seq) {
        try {
            // {{CHENGQI:
            // Action: Added; Timestamp: 2025-08-04 21:10:03 +08:00; Reason: 新增发送方会话级seq更新逻辑，确保发送方也能正确追踪自己在会话中的位置;
            // }}
            // {{START MODIFICATIONS}}
            log.debug("开始更新发送方会话级seq - 用户ID: {}, 会话ID: {}, seq: {}", senderId, conversationId, seq);

            // 构建会话seq映射
            Map<String, Long> conversationSeqs = new HashMap<>();
            conversationSeqs.put(conversationId, seq);

            // 调用MessageAckProcessor的更新逻辑，复用已有的seq更新机制
            String redisKey = RedisKeyConstants.getUserConversationSeqKey(senderId);

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
                    log.warn("解析发送方当前seq失败，使用默认值0 - 用户ID: {}, 会话ID: {}, 当前值: {}",
                            senderId, conversationId, currentSeqObj);
                    currentSeq = 0L;
                }
            }

            // 只有新seq大于当前seq时才更新
            if (seq > currentSeq) {
                redisTemplate.opsForHash().put(redisKey, conversationId, seq.toString()); // 转换为字符串存储，兼容StringRedisSerializer

                // 设置过期时间（30天）
                redisTemplate.expire(redisKey, RedisKeyConstants.CONVERSATION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);

                log.info("成功更新发送方会话级seq - 用户ID: {}, 会话ID: {}, 当前seq: {} -> 新seq: {}",
                        senderId, conversationId, currentSeq, seq);
            } else {
                log.debug("跳过发送方seq更新（新seq不大于当前seq） - 用户ID: {}, 会话ID: {}, 当前seq: {}, 新seq: {}",
                        senderId, conversationId, currentSeq, seq);
            }
            // {{END MODIFICATIONS}}

        } catch (Exception e) {
            log.error("更新发送方会话级seq失败 - 用户ID: {}, 会话ID: {}, seq: {}", senderId, conversationId, seq, e);
            // 不抛出异常，不影响主流程
        }
    }

    /**
     * 异步发送群聊消息发送确认回执给发送方
     *
     * 群聊场景下，使用会话级序列号作为userSeq
     * 按照技术方案要求，回执必须在消息成功持久化后才发送
     *
     * @param originalMessage 原始群聊消息
     * @param serverMsgId 服务端生成的消息ID
     * @param conversationSeq 会话级序列号
     */
    private void sendGroupReceiptToSenderAsync(ChatMessage originalMessage, String serverMsgId, Long conversationSeq) {
        try {
            // 使用当前时间作为服务端权威时间戳
            long serverTimestamp = System.currentTimeMillis();

            // 调用回执服务发送群聊确认回执
            messageSendReceiptService.sendGroupReceiptToSender(originalMessage, serverMsgId,
                                                             conversationSeq, serverTimestamp);

        } catch (Exception e) {
            // 回执发送失败不应影响主消息处理流程，只记录错误日志
            log.error("异步发送群聊消息回执失败 - 发送方: {}, 客户端序列号: {}, 服务端消息ID: {}",
                     originalMessage.getFromId(), originalMessage.getClientSeq(), serverMsgId, e);
        }
    }
}
}