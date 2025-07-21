package com.vanky.im.gateway.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户Channel管理器，维护用户ID与Channel的映射关系
 */
@Slf4j
@Component
public class UserChannelManager {

    // 用于在Channel中存储用户ID的属性键
    public static final AttributeKey<String> USER_ID_ATTR = AttributeKey.valueOf("userId");
    
    // 存储用户ID到Channel的映射
    private final ConcurrentHashMap<String, Channel> userChannelMap = new ConcurrentHashMap<>();
    
    /**
     * 绑定用户与Channel
     * 
     * @param userId 用户ID
     * @param channel 用户的Channel
     */
    public void bindChannel(String userId, Channel channel) {
        log.info("开始绑定用户 {} 到Channel {}, 当前在线用户数: {}", userId, channel.id().asShortText(), userChannelMap.size());

        // 先检查该用户是否已经有绑定的channel
        Channel oldChannel = userChannelMap.put(userId, channel);

        // 在channel中存储用户ID
        channel.attr(USER_ID_ATTR).set(userId);

        if (oldChannel != null && oldChannel != channel && oldChannel.isActive()) {
            log.info("用户 {} 在新的连接登录，关闭旧连接 {}", userId, oldChannel.id().asShortText());
            // 关闭旧的连接
            oldChannel.close();
        }

        log.info("用户 {} 成功绑定到Channel {}, 当前在线用户数: {}", userId, channel.id().asShortText(), userChannelMap.size());
    }
    
    /**
     * 解绑用户与Channel
     * 
     * @param userId 用户ID
     */
    public void unbindChannel(String userId) {
        Channel channel = userChannelMap.remove(userId);
        if (channel != null) {
            // 移除channel中的用户ID属性
            channel.attr(USER_ID_ATTR).set(null);
            log.info("用户 {} 解除与Channel {} 的绑定, 当前在线用户数: {}", userId, channel.id().asShortText(), userChannelMap.size());
        } else {
            log.warn("尝试解绑用户 {} 但未找到对应的Channel", userId);
        }
    }
    
    /**
     * 根据Channel解绑用户
     * 
     * @param channel 用户的Channel
     */
    public void unbindChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        
        String userId = getUserId(channel);
        if (userId != null) {
            unbindChannel(userId);
        }
    }
    
    /**
     * 获取用户的Channel
     * 
     * @param userId 用户ID
     * @return 用户的Channel，如果不存在则返回null
     */
    public Channel getChannel(String userId) {
        return userChannelMap.get(userId);
    }
    
    /**
     * 判断用户是否在线
     * 
     * @param userId 用户ID
     * @return 如果用户在线则返回true，否则返回false
     */
    public boolean isUserOnline(String userId) {
        Channel channel = userChannelMap.get(userId);
        return channel != null && channel.isActive();
    }
    
    /**
     * 获取Channel中存储的用户ID
     * 
     * @param channel 用户的Channel
     * @return 用户ID，如果不存在则返回null
     */
    public String getUserId(Channel channel) {
        if (channel == null) {
            return null;
        }
        return channel.attr(USER_ID_ATTR).get();
    }

    /**
     * 获取当前在线用户数量
     * @return 在线用户数量
     */
    public int getOnlineUserCount() {
        return userChannelMap.size();
    }

    /**
     * 打印当前所有在线用户信息（用于调试）
     */
    public void printOnlineUsers() {
        log.info("当前在线用户数: {}", userChannelMap.size());
        userChannelMap.forEach((userId, channel) -> {
            log.info("用户: {}, Channel: {}, 活跃状态: {}", userId, channel.id().asShortText(), channel.isActive());
        });
    }
}