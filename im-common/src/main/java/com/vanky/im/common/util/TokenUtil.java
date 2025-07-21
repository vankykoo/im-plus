package com.vanky.im.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author vanky
 * @date 2025/5/28
 * @description Token工具类，用于生成和验证token
 */
@Component
public class TokenUtil {

    // token有效期，默认24小时（单位：秒）
    private static final long TOKEN_EXPIRE_TIME = 24 * 60 * 60;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // Redis中token的key前缀
    private static final String TOKEN_PREFIX = "token:";
    
    /**
     * 生成token
     * @param userId 用户ID
     * @return token
     */
    public String generateToken(String userId) {
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
            
            // 存储token信息到Redis
            TokenInfo tokenInfo = new TokenInfo(userId, System.currentTimeMillis());
            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, tokenInfo, TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
            
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
    public String verifyToken(String token) {
        TokenInfo tokenInfo = (TokenInfo) redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (tokenInfo == null) {
            return null;
        }
        
        return tokenInfo.getUserId();
    }
    
    /**
     * 移除token
     * @param token token
     */
    public void removeToken(String token) {
        redisTemplate.delete(TOKEN_PREFIX + token);
    }
    
    /**
     * Token信息类
     */
    public static class TokenInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String userId;
        private long createTime;
        
        public TokenInfo() {
        }
        
        public TokenInfo(String userId, long createTime) {
            this.userId = userId;
            this.createTime = createTime;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }
    }
}