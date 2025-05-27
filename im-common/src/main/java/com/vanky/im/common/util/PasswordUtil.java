package com.vanky.im.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author vanky
 * @create 2025/5/26
 * @description 密码加密解密工具类
 */
public class PasswordUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    
    /**
     * 生成随机盐值
     * @return 盐值的Base64编码字符串
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * 使用SHA-256算法对密码进行加密
     * @param password 原始密码
     * @param salt 盐值
     * @return 加密后的密码
     */
    public static String encrypt(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            
            // 将盐值解码为字节数组
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            
            // 将密码和盐值组合
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[passwordBytes.length + saltBytes.length];
            System.arraycopy(passwordBytes, 0, combined, 0, passwordBytes.length);
            System.arraycopy(saltBytes, 0, combined, passwordBytes.length, saltBytes.length);
            
            // 计算哈希值
            byte[] hashBytes = digest.digest(combined);
            
            // 将哈希值转换为Base64编码
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密算法不可用", e);
        }
    }
    
    /**
     * 验证密码是否正确
     * @param inputPassword 输入的密码
     * @param encryptedPassword 数据库中存储的加密密码
     * @param salt 盐值
     * @return 密码是否匹配
     */
    public static boolean verify(String inputPassword, String encryptedPassword, String salt) {
        String calculatedHash = encrypt(inputPassword, salt);
        return calculatedHash.equals(encryptedPassword);
    }
    
    /**
     * 生成加密密码和盐值的组合字符串，格式为"加密密码:盐值"
     * @param password 原始密码
     * @return 加密密码和盐值的组合
     */
    public static String encryptWithSalt(String password) {
        String salt = generateSalt();
        String encryptedPassword = encrypt(password, salt);
        return encryptedPassword + ":" + salt;
    }
    
    /**
     * 从组合字符串中提取加密密码
     * @param combined 格式为"加密密码:盐值"的组合字符串
     * @return 加密密码
     */
    public static String getEncryptedPasswordFromCombined(String combined) {
        return combined.split(":")[0];
    }
    
    /**
     * 从组合字符串中提取盐值
     * @param combined 格式为"加密密码:盐值"的组合字符串
     * @return 盐值
     */
    public static String getSaltFromCombined(String combined) {
        return combined.split(":")[1];
    }
    
    /**
     * 验证密码是否与组合字符串中的加密密码匹配
     * @param inputPassword 输入的密码
     * @param combined 格式为"加密密码:盐值"的组合字符串
     * @return 密码是否匹配
     */
    public static boolean verifyWithCombined(String inputPassword, String combined) {
        String[] parts = combined.split(":");
        if (parts.length != 2) {
            return false;
        }
        String encryptedPassword = parts[0];
        String salt = parts[1];
        return verify(inputPassword, encryptedPassword, salt);
    }
} 