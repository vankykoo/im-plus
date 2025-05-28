package com.vanky.im.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vanky
 * @date 2025/5/28
 * @description Token工具类，用于生成和验证token
 */
public class TokenUtil {

    // token有效期，默认24小时（单位：毫秒）
    private static final long TOKEN_EXPIRE_TIME = 24 * 60 * 60 * 1000;
    
    // 存储token和用户ID的映射，用于验证token
    private static final ConcurrentHashMap<String, TokenInfo> TOKEN_MAP = new ConcurrentHashMap<>();
    
    /**
     * 生成token
     * @param userId 用户ID
     * @return token
     */
    public static String generateToken(String userId) {
        // 生成随机的UUID作为基础
        String base = UUID.randomUUID().toString();
        // 添加时间戳增加随机性
        String raw = base + ":" + userId + ":" + System.currentTimeMillis();
        
        try {
            // 使用SHA-256对原始数据进行摘要
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            
            // 将hash转为Base64编码作为token
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
            
            // 存储token信息
            TokenInfo tokenInfo = new TokenInfo(userId, System.currentTimeMillis());
            TOKEN_MAP.put(token, tokenInfo);
            
            return token;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Token生成失败", e);
        }
    }
    
    /**
     * 验证token是否有效
     * @param token token
     * @return 如果有效返回用户ID，否则返回null
     */
    public static String verifyToken(String token) {
        TokenInfo tokenInfo = TOKEN_MAP.get(token);
        if (tokenInfo == null) {
            return null;
        }
        
        // 检查token是否过期
        if (System.currentTimeMillis() - tokenInfo.getCreateTime() > TOKEN_EXPIRE_TIME) {
            // token过期，移除
            TOKEN_MAP.remove(token);
            return null;
        }
        
        return tokenInfo.getUserId();
    }
    
    /**
     * 移除token
     * @param token token
     */
    public static void removeToken(String token) {
        TOKEN_MAP.remove(token);
    }
    
    /**
     * 清除所有过期的token
     */
    public static void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        TOKEN_MAP.entrySet().removeIf(entry -> (now - entry.getValue().getCreateTime() > TOKEN_EXPIRE_TIME));
    }
    
    /**
     * Token信息类
     */
    private static class TokenInfo {
        private String userId;
        private long createTime;
        
        public TokenInfo(String userId, long createTime) {
            this.userId = userId;
            this.createTime = createTime;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getCreateTime() {
            return createTime;
        }
    }
} 