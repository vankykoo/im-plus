package com.vanky.im.testclient.storage;

import com.vanky.im.testclient.config.RedisConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * 本地消息存储管理器（Redis实现版本）
 * 负责管理客户端的本地消息存储和同步状态
 * 支持私聊写扩散和群聊读扩散的混合模式同步
 * 使用真正的Redis连接，替代本地文件存储，与服务端配置统一
 *
 * @author vanky
 * @create 2025/8/3
 * @description 客户端Redis存储实现，替代本地文件存储
 */
public class LocalMessageStorage {

    // Redis连接池
    private final JedisPool jedisPool;

    // Redis Key前缀
    private final String syncSeqPrefix;
    private final String conversationSeqPrefix;
    private final String messagesPrefix;
    private final String statsPrefix;

    public LocalMessageStorage() {
        // 初始化Key前缀
        this.syncSeqPrefix = RedisConfig.getSyncSeqPrefix();
        this.conversationSeqPrefix = RedisConfig.getConversationSeqPrefix();
        this.messagesPrefix = RedisConfig.getMessagesPrefix();
        this.statsPrefix = RedisConfig.getStatsPrefix();

        // 初始化Redis连接池
        this.jedisPool = createJedisPool();

        // 测试Redis连接
        testRedisConnection();

        System.out.println("LocalMessageStorage初始化完成 - 使用真正的Redis存储，替代本地文件存储");
        System.out.println("Redis配置: " + RedisConfig.getHost() + ":" + RedisConfig.getPort() +
                          ", 数据库: " + RedisConfig.getDatabase() + ", 密码: " +
                          (RedisConfig.getPassword().isEmpty() ? "无" : "已配置"));
        System.out.println("Redis连接池: 最大连接数=" + RedisConfig.getMaxTotal() +
                          ", 最大空闲=" + RedisConfig.getMaxIdle() +
                          ", 最小空闲=" + RedisConfig.getMinIdle());
    }

    /**
     * 创建Redis连接池
     */
    private JedisPool createJedisPool() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(RedisConfig.getMaxTotal());
            poolConfig.setMaxIdle(RedisConfig.getMaxIdle());
            poolConfig.setMinIdle(RedisConfig.getMinIdle());
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            String password = RedisConfig.getPassword();
            if (password != null && !password.trim().isEmpty()) {
                return new JedisPool(poolConfig, RedisConfig.getHost(), RedisConfig.getPort(),
                                   RedisConfig.getTimeout(), password, RedisConfig.getDatabase());
            } else {
                return new JedisPool(poolConfig, RedisConfig.getHost(), RedisConfig.getPort(),
                                   RedisConfig.getTimeout(), null, RedisConfig.getDatabase());
            }
        } catch (Exception e) {
            System.err.println("创建Redis连接池失败: " + e.getMessage());
            throw new RuntimeException("Redis连接池初始化失败", e);
        }
    }

    /**
     * 测试Redis连接
     */
    private void testRedisConnection() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            if ("PONG".equals(pong)) {
                System.out.println("Redis连接测试成功");
            } else {
                System.err.println("Redis连接测试失败，响应: " + pong);
            }
        } catch (Exception e) {
            System.err.println("Redis连接测试失败: " + e.getMessage());
            throw new RuntimeException("Redis连接测试失败", e);
        }
    }

    /**
     * 获取用户的最后同步序列号（私聊写扩散）
     *
     * @param userId 用户ID
     * @return 最后同步序列号，如果用户首次同步则返回0
     */
    public Long getLastSyncSeq(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = syncSeqPrefix + userId;
            String seqStr = jedis.get(key);
            return seqStr != null ? Long.parseLong(seqStr) : 0L;
        } catch (Exception e) {
            System.err.println("获取同步序列号失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 更新用户的最后同步序列号（私聊写扩散）
     *
     * @param userId 用户ID
     * @param lastSyncSeq 最后同步序列号
     */
    public void updateLastSyncSeq(String userId, Long lastSyncSeq) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = syncSeqPrefix + userId;
            jedis.setex(key, (int) RedisConfig.getClientDataExpire(), lastSyncSeq.toString());
            System.out.println("更新同步序列号成功 - 用户ID: " + userId + ", 序列号: " + lastSyncSeq);
        } catch (Exception e) {
            System.err.println("更新同步序列号失败 - 用户ID: " + userId + ", 序列号: " + lastSyncSeq + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========== 群聊同步点管理（读扩散模式） ==========

    /**
     * 获取用户的群聊同步点映射
     *
     * @param userId 用户ID
     * @return 群聊同步点映射，Key为conversationId，Value为lastSeq
     */
    public Map<String, Long> getConversationSeqMap(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;
            Map<String, String> seqMap = jedis.hgetAll(key);

            Map<String, Long> result = new HashMap<>();
            if (seqMap != null && !seqMap.isEmpty()) {
                for (Map.Entry<String, String> entry : seqMap.entrySet()) {
                    try {
                        result.put(entry.getKey(), Long.parseLong(entry.getValue()));
                    } catch (NumberFormatException e) {
                        System.err.println("解析群聊同步点失败 - 会话ID: " + entry.getKey() + ", 值: " + entry.getValue());
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("获取群聊同步点失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 更新用户的群聊同步点映射（增量更新，确保seq单调递增）
     *
     * @param userId 用户ID
     * @param conversationSeqMap 群聊同步点映射
     */
    public void updateConversationSeqMap(String userId, Map<String, Long> conversationSeqMap) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;

            // 改为增量更新，不删除现有数据
            if (!conversationSeqMap.isEmpty()) {
                int updatedCount = 0;

                for (Map.Entry<String, Long> entry : conversationSeqMap.entrySet()) {
                    String conversationId = entry.getKey();
                    Long newSeq = entry.getValue();

                    if (newSeq != null) {
                        // 获取当前已存储的seq
                        String currentSeqStr = jedis.hget(key, conversationId);
                        Long currentSeq = 0L;

                        if (currentSeqStr != null && !currentSeqStr.isEmpty()) {
                            try {
                                currentSeq = Long.parseLong(currentSeqStr);
                            } catch (NumberFormatException e) {
                                System.err.println("解析当前seq失败，使用默认值0 - 会话ID: " + conversationId + ", 当前值: " + currentSeqStr);
                                currentSeq = 0L;
                            }
                        }

                        // 只有新seq大于当前seq时才更新
                        if (newSeq > currentSeq) {
                            jedis.hset(key, conversationId, newSeq.toString());
                            updatedCount++;
                            System.out.println("增量更新群聊同步点 - 用户ID: " + userId +
                                             ", 会话ID: " + conversationId +
                                             ", 当前seq: " + currentSeq + " -> 新seq: " + newSeq);
                        } else {
                            System.out.println("跳过seq更新（新seq不大于当前seq） - 用户ID: " + userId +
                                             ", 会话ID: " + conversationId +
                                             ", 当前seq: " + currentSeq + ", 新seq: " + newSeq);
                        }
                    }
                }

                // 设置过期时间
                if (updatedCount > 0) {
                    jedis.expire(key, (int) RedisConfig.getClientDataExpire());
                }

                System.out.println("群聊同步点增量更新完成 - 用户ID: " + userId +
                                 ", 传入会话数量: " + conversationSeqMap.size() +
                                 ", 实际更新数量: " + updatedCount);
            }

        } catch (Exception e) {
            System.err.println("更新群聊同步点失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 更新单个群聊的同步点（确保seq单调递增）
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param lastSeq 最后同步序列号
     */
    public void updateConversationSeq(String userId, String conversationId, Long lastSeq) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;

            // 获取当前已存储的seq
            String currentSeqStr = jedis.hget(key, conversationId);
            Long currentSeq = 0L;

            if (currentSeqStr != null && !currentSeqStr.isEmpty()) {
                try {
                    currentSeq = Long.parseLong(currentSeqStr);
                } catch (NumberFormatException e) {
                    System.err.println("解析当前seq失败，使用默认值0 - 会话ID: " + conversationId + ", 当前值: " + currentSeqStr);
                    currentSeq = 0L;
                }
            }

            // 只有新seq大于当前seq时才更新
            if (lastSeq > currentSeq) {
                jedis.hset(key, conversationId, lastSeq.toString());
                jedis.expire(key, (int) RedisConfig.getClientDataExpire());

                System.out.println("更新群聊同步点成功 - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId +
                                 ", 当前seq: " + currentSeq + " -> 新seq: " + lastSeq);
            } else {
                System.out.println("跳过seq更新（新seq不大于当前seq） - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId +
                                 ", 当前seq: " + currentSeq + ", 新seq: " + lastSeq);
            }

        } catch (Exception e) {
            System.err.println("更新群聊同步点失败 - 用户ID: " + userId +
                             ", 会话ID: " + conversationId + ", 序列号: " + lastSeq + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 批量存储消息到Redis（替代本地文件存储）
     * 使用Redis List存储消息，支持容量限制
     *
     * @param userId 用户ID
     * @param messages 消息列表（JSON格式）
     * @return 存储是否成功
     */
    public boolean storeMessages(String userId, String messages) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = messagesPrefix + userId;

            // 添加消息到列表头部
            jedis.lpush(key, messages);

            // 限制列表长度，保留最新的N条消息
            long maxCount = RedisConfig.getMaxMessageCount();
            jedis.ltrim(key, 0, maxCount - 1);

            // 设置过期时间
            jedis.expire(key, (int) RedisConfig.getClientDataExpire());

            // 更新统计信息
            updateMessageStats(userId, 1);

            System.out.println("消息存储成功 - 用户ID: " + userId);
            return true;
        } catch (Exception e) {
            System.err.println("消息存储失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取用户的本地消息数量
     *
     * @param userId 用户ID
     * @return 本地消息数量
     */
    public long getLocalMessageCount(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = messagesPrefix + userId;
            return jedis.llen(key);
        } catch (Exception e) {
            System.err.println("获取本地消息数量失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            return 0L;
        }
    }
    
    /**
     * 更新消息统计信息
     *
     * @param userId 用户ID
     * @param increment 增量
     */
    private void updateMessageStats(String userId, long increment) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = statsPrefix + userId;

            String currentCountStr = jedis.hget(key, "message_count");
            long currentCount = currentCountStr != null ? Long.parseLong(currentCountStr) : 0L;

            Map<String, String> stats = new HashMap<>();
            stats.put("message_count", String.valueOf(currentCount + increment));
            stats.put("last_update", String.valueOf(System.currentTimeMillis()));

            jedis.hmset(key, stats);
            jedis.expire(key, (int) RedisConfig.getClientDataExpire());
        } catch (Exception e) {
            System.err.println("更新消息统计失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 清理用户的本地数据
     *
     * @param userId 用户ID
     */
    public void clearUserData(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 清理所有相关的键
            String syncSeqKey = syncSeqPrefix + userId;
            String conversationSeqKey = conversationSeqPrefix + userId;
            String messagesKey = messagesPrefix + userId;
            String statsKey = statsPrefix + userId;

            jedis.del(syncSeqKey, conversationSeqKey, messagesKey, statsKey);

            System.out.println("用户数据清理完成 - 用户ID: " + userId);
        } catch (Exception e) {
            System.err.println("用户数据清理失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取存储统计信息
     */
    public StorageStats getStorageStats() {
        try (Jedis jedis = jedisPool.getResource()) {
            // 统计Redis中的数据
            Set<String> syncSeqKeys = jedis.keys(syncSeqPrefix + "*");
            Set<String> messageKeys = jedis.keys(messagesPrefix + "*");

            long totalSize = 0;
            for (String key : messageKeys) {
                totalSize += jedis.llen(key);
            }

            return new StorageStats(totalSize, syncSeqKeys.size());
        } catch (Exception e) {
            System.err.println("获取存储统计信息失败: " + e.getMessage());
            return new StorageStats(0L, 0);
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            System.out.println("LocalMessageStorage资源已清理 - Redis连接池已关闭");
        }
    }

    // ========== 内部类定义 ==========

    /**
     * 存储统计信息
     */
    public static class StorageStats {
        private final long totalSize;
        private final int userCount;

        public StorageStats(long totalSize, int userCount) {
            this.totalSize = totalSize;
            this.userCount = userCount;
        }

        public long getTotalSize() { return totalSize; }
        public int getUserCount() { return userCount; }

        @Override
        public String toString() {
            return String.format("StorageStats{totalSize=%d messages, userCount=%d}", totalSize, userCount);
        }
    }
}
