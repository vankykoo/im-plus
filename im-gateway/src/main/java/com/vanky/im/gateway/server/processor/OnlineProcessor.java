package com.vanky.im.gateway.server.processor;

import com.vanky.im.gateway.session.UserSession;
import io.netty.channel.Channel;
import com.vanky.im.common.util.MsgGenerator;
import com.vanky.im.common.protocol.ChatMessage;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vanky
 * @create 2025/5/22 22:00
 * @description
 */
public class OnlineProcessor {

    // 单例实例
    private static volatile OnlineProcessor instance = null;

    /**
     * 用户会话存储
     * key: userId
     * value: UserSession
     */
    private final ConcurrentHashMap<String, UserSession> userSessionMap = new ConcurrentHashMap<>();
    
    /**
     * Channel到用户ID的映射，方便通过Channel查找用户
     * key: Channel的唯一标识
     * value: userId
     */
    private final ConcurrentHashMap<String, String> channelUserMap = new ConcurrentHashMap<>();

    //单例模式
    public static OnlineProcessor getInstance(){
        if (instance == null) {
            synchronized (OnlineProcessor.class) {
                if (instance == null) {
                    instance = new OnlineProcessor();
                }
            }
        }
        return instance;
    }

    private OnlineProcessor(){}

    /**
     * 用户上线
     * @param userId
     * @param channel
     */
    public void userOnline(String userId, Channel channel) {
        // 检查是否已在线
        UserSession oldSession = userSessionMap.get(userId);
        if (oldSession != null) {
            // 发送踢人通知
            ChatMessage kickMsg = MsgGenerator.generateKickoutMsg(userId);
            oldSession.getChannel().writeAndFlush(kickMsg);
            // 关闭旧连接
            oldSession.getChannel().close();
            // 移除旧的channel映射
            channelUserMap.remove(oldSession.getChannel().id().asLongText());
            System.out.println("用户[" + userId + "]已在线，踢掉旧连接");
        }
        // 获取客户端地址信息
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        String host = address.getHostString();
        int port = address.getPort();

        userSessionMap.put(userId, new UserSession(userId, host, port, channel));
        // 同时维护Channel到用户ID的映射
        channelUserMap.put(channel.id().asLongText(), userId);
        System.out.println("用户[" + userId + "]上线，当前在线用户数：" + userSessionMap.size());

        // 发送登录成功回执
        ChatMessage loginSuccessMsg = MsgGenerator.generateLoginSuccessMsg(userId);
        channel.writeAndFlush(loginSuccessMsg);
    }

    /**
     * 通过用户ID用户下线
     * @param userId
     */
    public void userOffline(String userId) {
        UserSession session = userSessionMap.remove(userId);
        if (session != null) {
            // 同时移除Channel映射
            channelUserMap.remove(session.getChannel().id().asLongText());
            System.out.println("用户[" + userId + "]下线，当前在线用户数：" + userSessionMap.size());
        }
    }
    
    /**
     * 通过Channel使用户下线
     * @param channel
     */
    public void userOfflineByChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        String userId = channelUserMap.remove(channelId);
        if (userId != null) {
            userSessionMap.remove(userId);
            System.out.println("用户[" + userId + "]通过Channel下线，当前在线用户数：" + userSessionMap.size());
        }
    }
    
    /**
     * 通过Channel获取用户ID
     * @param channel
     * @return 用户ID，如果找不到则返回null
     */
    public String getUserIdByChannel(Channel channel) {
        return channelUserMap.get(channel.id().asLongText());
    }

    /**
     * 获取用户对应的channel
     * @param userId 用户id
     * @return
     */
    public Channel getUserChannel(String userId) {
        UserSession userSession = userSessionMap.get(userId);
        if (userSession == null) {
            System.err.println("用户不在线: " + userId);
            return null;
        }
        return userSession.getChannel();
    }
    
    /**
     * 判断用户是否在线
     * @param userId 用户id
     * @return
     */
    public boolean isUserOnline(String userId) {
        return userSessionMap.containsKey(userId);
    }
    
    /**
     * 获取所有在线用户
     * @return
     */
    public Map<String, UserSession> getAllOnlineUsers() {
        return userSessionMap;
    }
    
    /**
     * 获取在线用户数量
     * @return
     */
    public int getOnlineUserCount() {
        return userSessionMap.size();
    }
}
