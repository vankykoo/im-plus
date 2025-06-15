package com.vanky.im.gateway.server.processor.server;

import com.vanky.im.common.util.RedisUtils;
import com.vanky.im.gateway.session.SessionConstants;
import com.vanky.im.gateway.session.UserSession;
import io.netty.channel.Channel;
import com.vanky.im.common.util.MsgGenerator;
import com.vanky.im.common.protocol.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author vanky
 * @create 2025/5/22 22:00
 * @description 用户在线处理器，负责管理用户的会话信息
 */
@Component
public class OnlineProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OnlineProcessor.class);

    // 单例实例，用于向后兼容
    private static volatile OnlineProcessor instance = null;

    /**
     * 本地会话缓存，提高访问速度（可选）
     * key: userId
     * value: UserSession
     */
    private final ConcurrentHashMap<String, UserSession> localSessionCache = new ConcurrentHashMap<>();

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 服务器节点ID，用于标识当前节点
     */
    @Value("${server.node-id:node-1}")
    private String nodeId;

    /**
     * 初始化方法，设置单例实例
     */
    @PostConstruct
    public void init() {
        instance = this;
        logger.info("OnlineProcessor initialized with node ID: {}", nodeId);
    }

    /**
     * 获取单例实例（向后兼容）
     */
    public static OnlineProcessor getInstance() {
        return instance;
    }

    /**
     * 用户上线
     * @param userId 用户ID
     * @param channel 用户连接的通道
     */
    public void userOnline(String userId, Channel channel) {
        // 获取客户端地址信息
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        String host = address.getHostString();
        int port = address.getPort();
        String channelId = channel.id().asLongText();

        // 检查用户是否已在其他节点登录
        boolean isMultipleLogin = isUserOnline(userId);

        if (isMultipleLogin) {
            // 从Redis获取旧会话信息
            Map<Object, Object> sessionMap = redisUtils.hashGetAll(SessionConstants.getUserSessionKey(userId));
            if (sessionMap != null && !sessionMap.isEmpty()) {
                String oldNodeId = (String) sessionMap.get("nodeId");
                String oldChannelId = (String) sessionMap.get("channelId");
                
                // 如果在同一节点登录
                if (nodeId.equals(oldNodeId)) {
                    // 获取旧的Channel
                    UserSession oldSession = localSessionCache.get(userId);
                    if (oldSession != null && oldSession.getChannel() != null) {
                        // 发送踢人通知
                        ChatMessage kickMsg = MsgGenerator.generateKickoutMsg(userId);
                        oldSession.getChannel().writeAndFlush(kickMsg);
                        // 关闭旧连接
                        oldSession.getChannel().close();
                    }
                } else {
                    // 需要通过消息队列或其他方式通知其他节点踢出用户
                    logger.info("用户[{}]在其他节点[{}]已登录，应通知该节点踢出用户", userId, oldNodeId);
                    // TODO: 通过消息队列发送踢人消息
                }
                
                // 删除旧的Channel映射
                if (oldChannelId != null) {
                    redisUtils.delete(SessionConstants.getChannelUserKey(oldChannelId));
                }
            }
            
            // 从Redis在线用户集合中移除旧用户（将在下面重新添加）
            redisUtils.setRemove(SessionConstants.ONLINE_USERS_KEY, userId);
        }

        // 创建新的UserSession对象
        UserSession userSession = new UserSession(userId, host, port, nodeId, channel);
        
        // 添加到本地缓存
        localSessionCache.put(userId, userSession);
        
        // 存储会话信息到Redis的Hash结构中
        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("userId", userId);
        sessionMap.put("host", host);
        sessionMap.put("port", port);
        sessionMap.put("nodeId", nodeId);
        sessionMap.put("channelId", channelId); // 存储Channel ID而不是Channel对象
        
        // 使用Hash结构存储会话信息
        redisUtils.hashSetAll(SessionConstants.getUserSessionKey(userId), sessionMap);
        redisUtils.expire(SessionConstants.getUserSessionKey(userId), SessionConstants.SESSION_EXPIRE_TIME, TimeUnit.SECONDS);
        
        // 存储Channel到用户的映射
        redisUtils.setString(SessionConstants.getChannelUserKey(channelId), userId);
        redisUtils.expire(SessionConstants.getChannelUserKey(channelId), SessionConstants.CHANNEL_EXPIRE_TIME, TimeUnit.SECONDS);
        
        // 将用户添加到在线用户集合
        redisUtils.setAdd(SessionConstants.ONLINE_USERS_KEY, userId);

        logger.info("用户[{}]上线，节点[{}]，当前在线用户数：{}", userId, nodeId, getOnlineUserCount());

        // 发送登录成功回执
        ChatMessage loginSuccessMsg = MsgGenerator.generateLoginSuccessMsg(userId);
        channel.writeAndFlush(loginSuccessMsg);
    }

    /**
     * 通过用户ID使用户下线
     * @param userId 用户ID
     */
    public void userOffline(String userId) {
        // 从本地缓存移除
        UserSession session = localSessionCache.remove(userId);
        
        // 从Redis中获取会话信息
        Map<Object, Object> sessionMap = redisUtils.hashGetAll(SessionConstants.getUserSessionKey(userId));
        if (sessionMap != null && !sessionMap.isEmpty()) {
            // 获取Channel ID
            String channelId = (String) sessionMap.get("channelId");
            if (channelId != null) {
                // 删除Channel映射
                redisUtils.delete(SessionConstants.getChannelUserKey(channelId));
            }
            
            // 删除用户会话
            redisUtils.delete(SessionConstants.getUserSessionKey(userId));
            
            // 从在线用户集合中移除
            redisUtils.setRemove(SessionConstants.ONLINE_USERS_KEY, userId);
            
            logger.info("用户[{}]下线，节点[{}]，当前在线用户数：{}", 
                    userId, sessionMap.get("nodeId"), getOnlineUserCount());
        } else if (session != null) {
            // 如果Redis中没有但本地缓存有（可能是因为Redis过期），仍然记录日志
            logger.info("用户[{}]下线（仅本地缓存有记录），节点[{}]", userId, nodeId);
        }
    }
    
    /**
     * 通过Channel使用户下线
     * @param channel 用户连接的通道
     */
    public void userOfflineByChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        
        // 从Redis中获取用户ID
        String userId = redisUtils.getString(SessionConstants.getChannelUserKey(channelId));
        
        if (userId != null) {
            // 删除Channel映射
            redisUtils.delete(SessionConstants.getChannelUserKey(channelId));
            
            // 从本地缓存移除
            localSessionCache.remove(userId);
            
            // 删除用户会话
            redisUtils.delete(SessionConstants.getUserSessionKey(userId));
            
            // 从在线用户集合中移除
            redisUtils.setRemove(SessionConstants.ONLINE_USERS_KEY, userId);
            
            logger.info("用户[{}]通过Channel下线，节点[{}]，当前在线用户数：{}", 
                    userId, nodeId, getOnlineUserCount());
        }
    }
    
    /**
     * 通过Channel获取用户ID
     * @param channel 用户连接的通道
     * @return 用户ID，如果找不到则返回null
     */
    public String getUserIdByChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        return redisUtils.getString(SessionConstants.getChannelUserKey(channelId));
    }

    /**
     * 获取用户对应的channel
     * @param userId 用户id
     * @return Channel对象，如果用户不在线或不在当前节点则返回null
     */
    public Channel getUserChannel(String userId) {
        // 先检查本地缓存
        UserSession cachedSession = localSessionCache.get(userId);
        if (cachedSession != null && cachedSession.getChannel() != null && cachedSession.getChannel().isActive()) {
            return cachedSession.getChannel();
        }
        
        // 如果本地缓存没有，检查Redis
        Map<Object, Object> sessionMap = redisUtils.hashGetAll(SessionConstants.getUserSessionKey(userId));
        if (sessionMap != null && !sessionMap.isEmpty()) {
            String sessionNodeId = (String) sessionMap.get("nodeId");
            
            // 如果用户在当前节点，但本地缓存没有（可能已过期），返回null
            if (nodeId.equals(sessionNodeId)) {
                logger.warn("用户[{}]在当前节点但本地缓存无记录", userId);
                return null;
            } else {
                // 用户在其他节点，需要通过节点间通信获取
                logger.info("用户[{}]在其他节点[{}]，需要通过节点间通信发送消息", userId, sessionNodeId);
                return null;
            }
        }
        
        logger.warn("用户[{}]不在线", userId);
        return null;
    }
    
    /**
     * 判断用户是否在线
     * @param userId 用户id
     * @return 是否在线
     */
    public boolean isUserOnline(String userId) {
        return redisUtils.hasKey(SessionConstants.getUserSessionKey(userId));
    }
    
    /**
     * 获取在线用户数量
     * @return 在线用户数量
     */
    public int getOnlineUserCount() {
        Long size = redisUtils.setSize(SessionConstants.ONLINE_USERS_KEY);
        return size != null ? size.intValue() : 0;
    }
    
    /**
     * 获取所有在线用户
     * @return 在线用户会话信息
     */
    public Map<String, UserSession> getAllOnlineUsers() {
        Map<String, UserSession> result = new HashMap<>();
        
        // 获取所有在线用户的ID
        Set<Object> userIds = redisUtils.setMembers(SessionConstants.ONLINE_USERS_KEY);
        if (userIds != null && !userIds.isEmpty()) {
            for (Object userIdObj : userIds) {
                String userId = (String) userIdObj;
                Map<Object, Object> sessionMap = redisUtils.hashGetAll(SessionConstants.getUserSessionKey(userId));
                
                if (sessionMap != null && !sessionMap.isEmpty()) {
                    String host = (String) sessionMap.get("host");
                    int port = Integer.parseInt(sessionMap.get("port").toString());
                    String sessionNodeId = (String) sessionMap.get("nodeId");
                    
                    // 创建会话对象，但不包含Channel实例
                    UserSession session = new UserSession();
                    session.setUserId(userId);
                    session.setHost(host);
                    session.setPort(port);
                    session.setNodeId(sessionNodeId);
                    
                    // 如果是当前节点，从本地缓存获取Channel
                    if (nodeId.equals(sessionNodeId)) {
                        UserSession localSession = localSessionCache.get(userId);
                        if (localSession != null) {
                            session.setChannel(localSession.getChannel());
                        }
                    }
                    
                    result.put(userId, session);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 刷新会话过期时间
     * @param userId 用户ID
     */
    public void refreshSession(String userId) {
        if (isUserOnline(userId)) {
            redisUtils.expire(SessionConstants.getUserSessionKey(userId), SessionConstants.SESSION_EXPIRE_TIME, TimeUnit.SECONDS);
            
            // 获取channelId并刷新其过期时间
            Map<Object, Object> sessionMap = redisUtils.hashGetAll(SessionConstants.getUserSessionKey(userId));
            if (sessionMap != null && !sessionMap.isEmpty()) {
                String channelId = (String) sessionMap.get("channelId");
                if (channelId != null) {
                    redisUtils.expire(SessionConstants.getChannelUserKey(channelId), SessionConstants.CHANNEL_EXPIRE_TIME, TimeUnit.SECONDS);
                }
            }
        }
    }
    
    /**
     * 获取会话所在节点ID
     * @param userId 用户ID
     * @return 节点ID，如果用户不在线则返回null
     */
    public String getUserNodeId(String userId) {
        if (isUserOnline(userId)) {
            Object nodeId = redisUtils.hashGet(SessionConstants.getUserSessionKey(userId), "nodeId");
            return nodeId != null ? nodeId.toString() : null;
        }
        return null;
    }
}
