package com.vanky.im.gateway.server.processor;

import com.vanky.im.common.constant.MsgContentConstant;
import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.constant.MessageTypeConstants;

import com.vanky.im.common.model.UserSession;
import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.common.util.MsgGenerator;
import com.vanky.im.common.util.TokenUtil;
import com.vanky.im.gateway.session.UserChannelManager;
import com.vanky.im.gateway.server.processor.client.PrivateMsgProcessor;
import com.vanky.im.gateway.server.processor.client.GroupMsgProcessor;
import com.vanky.im.gateway.timeout.TimeoutManager;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author vanky
 * @create 2025/6/8
 * @description 统一消息分发器，根据消息类型分发到对应的处理器
 */
@Slf4j
@Component
public class IMServiceHandler {

    @Autowired
    private PrivateMsgProcessor privateMsgProcessor;
    
    @Autowired
    private GroupMsgProcessor groupMsgProcessor;

    @Autowired
    private com.vanky.im.gateway.mq.MessageQueueService messageQueueService;

    @Autowired
    private UserChannelManager userChannelManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private TimeoutManager timeoutManager;

    @Value("${server.node-id}")
    private String gatewayNodeId;

    /**
     * 处理消息，根据消息类型分发到对应的处理器
     * @param msg 聊天消息
     * @param channel 客户端连接通道
     */
    public void handleMessage(ChatMessage msg, Channel channel) {
        long startTime = System.currentTimeMillis();
        try {
            int messageType = msg.getType();
            log.info("收到消息 - 类型: {}, 发送方: {}, 接收方: {}, 消息ID: {}, Channel: {}",
                    messageType, msg.getFromId(), msg.getToId(), msg.getUid(), channel.id().asShortText());
            
            // 客户端到服务端消息处理
            if (messageType == MessageTypeConstants.LOGIN_REQUEST) {
                // 处理登录请求
                handleLogin(msg, channel);
            } else if (messageType == MessageTypeConstants.LOGOUT_REQUEST) {
                // 处理登出请求
                handleLogout(msg, channel);
            } else if (messageType == MessageTypeConstants.HEARTBEAT) {
                // 处理心跳消息
                handleHeartbeat(msg, channel);
            } else if (messageType == MessageTypeConstants.MESSAGE_ACK) {
                // 处理消息确认
                handleMessageAck(msg, channel);
            } else if (messageType == MessageTypeConstants.GROUP_CONVERSATION_ACK) {
                // 处理群聊会话ACK确认
                handleGroupConversationAck(msg, channel);
            }
            // 客户端到客户端消息处理
            else if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE ||
                     messageType == MessageTypeConstants.GROUP_CHAT_MESSAGE) {
                
                // 对于UDP连接，需要额外的Token验证
                if (isUdpChannel(channel)) {
                    if (!validateToken(msg.getFromId(), msg.getToken())) {
                        log.warn("UDP消息Token验证失败 - 用户: {}, 消息类型: {}", msg.getFromId(), messageType);
                        return;
                    }
                }
                
                if (messageType == MessageTypeConstants.PRIVATE_CHAT_MESSAGE) {
                    // 私聊消息
                    privateMsgProcessor.process(msg, channel);
                } else {
                    // 群聊消息
                    groupMsgProcessor.process(msg, channel);
                }
            } else {
                log.warn("未知消息类型: {}, 发送方: {}, 消息ID: {}", 
                        messageType, msg.getFromId(), msg.getUid());
            }
            
        } catch (Exception e) {
            log.error("处理消息时发生错误 - 消息类型: {}, 发送方: {}, 消息ID: {}, 错误: {}",
                    msg.getType(), msg.getFromId(), msg.getUid(), e.getMessage(), e);
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            if (processingTime > 100) { // 如果处理时间超过100ms，记录警告
                log.warn("消息处理耗时过长: {}ms, 消息类型: {}, 消息ID: {}",
                        processingTime, msg.getType(), msg.getUid());
            }
        }
    }
    
    /**
     * 处理用户登录
     */
    private void handleLogin(ChatMessage msg, Channel channel) {
        String userId = msg.getFromId();
        String token = msg.getToken();

        log.info("处理用户登录请求 - 用户: {}, Channel: {}, Token长度: {}",
                userId, channel.id().asShortText(), token != null ? token.length() : 0);

        // 验证token有效性
        if (!validateToken(userId, token)) {
            log.warn("用户登录Token无效 - 用户: {}, Channel: {}, 关闭连接", userId, channel.id().asShortText());
            // 发送登录失败消息
            ChatMessage loginFailedMsg = ChatMessage.newBuilder()
                    .setType(MessageTypeConstants.LOGIN_RESPONSE)
                    .setContent("登录失败：无效的Token")
                    .setFromId("system")
                    .setToId(userId)
                    .setUid(msg.getUid())
                    .setSeq(msg.getSeq())
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            // 发送失败消息后关闭连接
            channel.writeAndFlush(loginFailedMsg).addListener(future -> {
                log.info("登录失败消息已发送，关闭连接 - 用户: {}, Channel: {}", userId, channel.id().asShortText());
                channel.close();
            });
            return;
        }
        
        log.info("用户登录 - 用户: {}, Channel: {}", userId, channel.id().asShortText());
        
        try {
            // 1. 绑定用户ID和Channel
            userChannelManager.bindChannel(userId, channel);

            // 2. 立即发送登录成功消息（不等待Redis操作完成）
            ChatMessage loginSuccessMsg = MsgGenerator.generateLoginSuccessMsg(userId);
            channel.writeAndFlush(loginSuccessMsg);

            log.info("用户登录成功 - 用户: {}, 网关: {}", userId, gatewayNodeId);

            // 3. 异步处理Redis操作（不阻塞EventLoop）
            CompletableFuture.runAsync(() -> {
                try {
                    // 创建用户会话
                    UserSession userSession = new UserSession(userId, channel.localAddress().toString(),
                            0, gatewayNodeId, channel);

                    // 将用户会话存入Redis
                    String sessionKey = SessionConstants.getUserSessionKey(userId);
                    redisTemplate.opsForValue().set(sessionKey, userSession,
                            RedisKeyConstants.SESSION_EXPIRE_TIME, TimeUnit.SECONDS);

                    // 将用户ID添加到在线用户集合
                    redisTemplate.opsForSet().add(RedisKeyConstants.ONLINE_USERS_KEY, userId);

                    log.debug("用户会话Redis存储完成 - 用户: {}", userId);
                } catch (Exception e) {
                    log.error("用户会话Redis存储异常 - 用户: {}", userId, e);
                }
            });

        } catch (Exception e) {
            log.error("用户登录处理异常 - 用户: {}", userId, e);
        }
    }
    
    /**
     * 处理用户登出
     */
    private void handleLogout(ChatMessage msg, Channel channel) {
        String userId = msg.getFromId();
        log.info("用户登出 - 用户: {}", userId);
        
        try {
            // 1. 解绑用户ID和Channel
            userChannelManager.unbindChannel(userId);
            
            // 2. 从Redis删除用户会话
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            redisTemplate.delete(sessionKey);
            
            // 3. 将用户ID从在线用户集合中移除
            redisTemplate.opsForSet().remove(RedisKeyConstants.ONLINE_USERS_KEY, userId);
            
            log.info("用户登出成功 - 用户: {}", userId);
        } catch (Exception e) {
            log.error("用户登出处理异常 - 用户: {}", userId, e);
        }
    }
    
    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(ChatMessage msg, Channel channel) {
        String userId = msg.getFromId();
        
        // 对于UDP连接，需要额外的Token验证
        if (isUdpChannel(channel) && !validateToken(userId, msg.getToken())) {
            log.warn("UDP心跳消息Token验证失败 - 用户: {}", userId);
            return;
        }
        
        try {
            // 1. 刷新Redis中用户会话的过期时间
            String sessionKey = SessionConstants.getUserSessionKey(userId);
            redisTemplate.expire(sessionKey, RedisKeyConstants.SESSION_EXPIRE_TIME, TimeUnit.SECONDS);
            
            // 2. 发送心跳响应
            ChatMessage heartbeatResponse = MsgGenerator.generateHeartbeatResponseMsg(userId);
            channel.writeAndFlush(heartbeatResponse);
            
            log.debug("心跳处理成功 - 用户: {}", userId);
        } catch (Exception e) {
            log.error("心跳处理异常 - 用户: {}", userId, e);
        }
    }
    
    /**
     * 判断是否为UDP通道
     * @param channel 通道
     * @return 如果是UDP通道则返回true
     */
    private boolean isUdpChannel(Channel channel) {
        return channel instanceof DatagramChannel;
    }
    
    /**
     * 验证用户Token
     * @param userId 用户ID
     * @param token 令牌
     * @return 如果验证成功则返回true
     */
    private boolean validateToken(String userId, String token) {
        log.info("开始验证Token - 用户: {}, Token: {}", userId, token);

        // 如果token为空，则验证失败
        if (token == null || token.isEmpty()) {
            log.warn("Token验证失败 - 用户: {}, 原因: Token为空", userId);
            return false;
        }

        try {
            // 使用TokenUtil验证token
            String tokenUserId = tokenUtil.verifyToken(token);
            log.info("Token解析结果 - 用户: {}, Token中的用户ID: {}", userId, tokenUserId);

            boolean isValid = userId != null && userId.equals(tokenUserId);
            log.info("Token验证结果 - 用户: {}, 验证结果: {}", userId, isValid);
            return isValid;
        } catch (Exception e) {
            log.error("Token验证异常 - 用户: {}, Token: {}", userId, token, e);
            return false;
        }
    }

    /**
     * 处理消息确认
     * @param msg 确认消息
     * @param channel 客户端连接通道
     */
    private void handleMessageAck(ChatMessage msg, Channel channel) {
        String userId = msg.getFromId();
        String msgId = msg.getUid();
        String seq = msg.getSeq();

        log.info("收到消息确认 - 用户: {}, 消息ID: {}, 序列号: {}, Channel: {}",
                userId, msgId, seq, channel.id().asShortText());

        try {
            // 取消超时重发任务
            boolean cancelled = timeoutManager.cancelTask(msgId);
            if (cancelled) {
                log.debug("取消超时任务成功 - 消息ID: {}, 用户: {}", msgId, userId);
            } else {
                log.debug("取消超时任务失败，任务可能不存在 - 消息ID: {}, 用户: {}", msgId, userId);
            }

            // 将ACK消息发送到消息队列，由im-message-server处理
            messageQueueService.sendAckToMessageServer(msgId, seq, userId);

            log.debug("ACK消息已转发到消息服务器 - 消息ID: {}, 序列号: {}, 用户: {}", msgId, seq, userId);

        } catch (Exception e) {
            log.error("处理消息确认失败 - 用户: {}, 消息ID: {}, 序列号: {}", userId, msgId, seq, e);
        }
    }

    /**
     * 处理群聊会话ACK确认
     * @param msg 群聊会话ACK消息
     * @param channel 客户端连接通道
     */
    private void handleGroupConversationAck(ChatMessage msg, Channel channel) {
        String userId = msg.getFromId();
        String content = msg.getContent(); // conversationId1:seq1,conversationId2:seq2

        log.info("收到群聊会话ACK确认 - 用户: {}, 内容: {}, Channel: {}",
                userId, content, channel.id().asShortText());

        try {
            // 将群聊会话ACK消息发送到消息队列，由im-message-server处理
            messageQueueService.sendGroupConversationAckToMessageServer(msg);

            log.debug("群聊会话ACK消息已转发到消息服务器 - 用户: {}, 内容: {}", userId, content);

        } catch (Exception e) {
            log.error("处理群聊会话ACK确认失败 - 用户: {}, 内容: {}", userId, content, e);
        }
    }
}