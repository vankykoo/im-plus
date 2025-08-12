package com.vanky.im.sequence.util;

import com.vanky.im.sequence.constant.SequenceConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * 分段键生成工具类
 * 负责根据业务key生成对应的分段键（section_key）
 *
 * 分段键格式：
 * - 用户序列号: u_{section_id} (例如: u_123)
 * - 会话序列号: c_{section_id} (例如: c_456)
 *
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
public class SectionIdGenerator {

    /**
     * 默认分段数量
     */
    private static final int DEFAULT_SECTION_COUNT = SequenceConstants.Section.DEFAULT_SECTION_COUNT;

    /**
     * 用户序列号分段键前缀
     */
    public static final String USER_PREFIX = "u_";

    /**
     * 会话序列号分段键前缀
     */
    public static final String CONVERSATION_PREFIX = "c_";

    /**
     * 根据业务key生成分段键
     *
     * @param businessKey 业务key，如 "user_12345" 或 "group_67890"
     * @return 分段键，如 "u_123" 或 "c_456"
     */
    public static String generateSectionKey(String businessKey) {
        if (businessKey == null || businessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Business key cannot be null or empty");
        }

        try {
            if (businessKey.startsWith("user_")) {
                return generateUserSectionKey(businessKey);
            } else if (businessKey.startsWith("group_") || businessKey.startsWith("conversation_")) {
                return generateConversationSectionKey(businessKey);
            } else {
                // 默认按用户处理
                log.warn("Unknown business key format: {}, treating as user key", businessKey);
                return generateUserSectionKey(businessKey);
            }
        } catch (Exception e) {
            log.error("Failed to generate section key for business key: {}", businessKey, e);
            // 降级处理：使用hashCode取模
            return generateFallbackSectionKey(businessKey);
        }
    }

    /**
     * 生成用户分段键
     * 策略：提取用户ID数字部分，按分段数量取模
     *
     * @param userKey 用户key，如 "user_12345"
     * @return 分段键，如 "u_123"
     */
    private static String generateUserSectionKey(String userKey) {
        try {
            // 提取用户ID
            String userIdStr = userKey.substring(userKey.lastIndexOf("_") + 1);
            long userId = Long.parseLong(userIdStr);

            // 按分段数量取模
            int sectionIndex = (int) (userId % DEFAULT_SECTION_COUNT);

            return USER_PREFIX + sectionIndex;
        } catch (Exception e) {
            log.warn("Failed to parse user id from key: {}, using fallback", userKey);
            return generateFallbackSectionKey(userKey);
        }
    }

    /**
     * 生成会话分段键
     * 策略：对会话ID进行哈希，按分段数量取模
     *
     * @param conversationKey 会话key，如 "group_67890" 或 "conversation_abc123"
     * @return 分段键，如 "c_456"
     */
    private static String generateConversationSectionKey(String conversationKey) {
        try {
            // 对整个key进行哈希
            int hashCode = Math.abs(conversationKey.hashCode());
            int sectionIndex = hashCode % DEFAULT_SECTION_COUNT;

            return CONVERSATION_PREFIX + sectionIndex;
        } catch (Exception e) {
            log.warn("Failed to generate conversation section key for key: {}, using fallback", conversationKey);
            return generateFallbackSectionKey(conversationKey);
        }
    }

    /**
     * 降级分段键生成
     * 当其他方法失败时使用
     *
     * @param businessKey 业务key
     * @return 分段键
     */
    private static String generateFallbackSectionKey(String businessKey) {
        int hashCode = Math.abs(businessKey.hashCode());
        int sectionIndex = hashCode % DEFAULT_SECTION_COUNT;

        // 根据key前缀决定分段类型
        if (businessKey.startsWith("user_")) {
            return USER_PREFIX + sectionIndex;
        } else {
            return CONVERSATION_PREFIX + sectionIndex;
        }
    }

    /**
     * 获取分段数量
     * 
     * @return 分段数量
     */
    public static int getSectionCount() {
        return DEFAULT_SECTION_COUNT;
    }

    /**
     * 验证分段键格式
     *
     * @param sectionKey 分段键
     * @return 是否有效
     */
    public static boolean isValidSectionKey(String sectionKey) {
        if (sectionKey == null || sectionKey.trim().isEmpty()) {
            return false;
        }

        return sectionKey.startsWith(USER_PREFIX) || sectionKey.startsWith(CONVERSATION_PREFIX);
    }

    /**
     * 根据分段键获取业务类型
     *
     * @param sectionKey 分段键
     * @return 业务类型：USER 或 CONVERSATION
     */
    public static String getBusinessType(String sectionKey) {
        if (sectionKey == null) {
            return "UNKNOWN";
        }

        if (sectionKey.startsWith(USER_PREFIX)) {
            return "USER";
        } else if (sectionKey.startsWith(CONVERSATION_PREFIX)) {
            return "CONVERSATION";
        } else {
            return "UNKNOWN";
        }
    }
}
