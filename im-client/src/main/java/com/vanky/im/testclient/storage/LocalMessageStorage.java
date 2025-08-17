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
 * 客户端存储管理器（Redis实现版本）
 * 负责管理客户端的同步序列号存储，符合README文档中的客户端存储Redis Key定义
 * 只保留两个Redis Key：
 * 1. client:sync:seq:{userId} - 客户端用户级全局序列号
 * 2. client:sync:conversation_seq:{userId}:{conversationId} - 客户端会话级全局序列号
 *
 * @author vanky
 * @create 2025/8/3
 * @description 客户端Redis存储实现，符合README文档定义
 */
public class LocalMessageStorage {

    // Redis连接池
    private final JedisPool jedisPool;

    // Redis Key前缀
    private final String syncSeqPrefix;
    private final String conversationSeqPrefix;

    public LocalMessageStorage() {
        // 初始化Key前缀
        this.syncSeqPrefix = RedisConfig.getSyncSeqPrefix();
        this.conversationSeqPrefix = RedisConfig.getConversationSeqPrefix();

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
            jedis.setex(key, (int) RedisConfig.getClientUserSeqExpire(), lastSyncSeq.toString());
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
        System.out.println("[DEBUG] 开始批量更新群聊同步点 - 用户: " + userId + ", 会话数量: " + conversationSeqMap.size());

        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;

            // 改为增量更新，不删除现有数据
            if (!conversationSeqMap.isEmpty()) {
                int updatedCount = 0;

                for (Map.Entry<String, Long> entry : conversationSeqMap.entrySet()) {
                    String conversationId = entry.getKey();
                    Long newSeq = entry.getValue();

                    System.out.println("[DEBUG] 处理会话同步点 - 会话: " + conversationId + ", 新seq: " + newSeq);

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
                    jedis.expire(key, (int) RedisConfig.getClientConversationSeqExpire());
                }

                System.out.println("[DEBUG] 群聊同步点增量更新完成 - 用户ID: " + userId +
                                 ", 传入会话数量: " + conversationSeqMap.size() +
                                 ", 实际更新数量: " + updatedCount);
            } else {
                System.out.println("[DEBUG] 传入的会话同步点映射为空，跳过更新");
            }

        } catch (Exception e) {
            System.err.println("[ERROR] 更新群聊同步点失败 - 用户ID: " + userId + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取单个群聊会话的最后序列号
     * 从Redis中获取指定用户在指定群聊会话中的最后序列号
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return 最后序列号，如果不存在则返回0
     */
    public long getConversationLastSeq(String userId, String conversationId) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-06 21:32:28 +08:00; Reason: 实现从Redis获取单个群聊会话最后序列号的方法，使用Redis Key格式im:client:conversation:seq:{user_id};
        // }}
        // {{START MODIFICATIONS}}
        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;
            String seqStr = jedis.hget(key, conversationId);

            if (seqStr != null && !seqStr.isEmpty()) {
                try {
                    long seq = Long.parseLong(seqStr);
                    return seq;
                } catch (NumberFormatException e) {
                    System.err.println("解析群聊会话序列号失败 - 用户ID: " + userId +
                                     ", 会话ID: " + conversationId + ", 值: " + seqStr);
                    return 0L;
                }
            } else {
                System.out.println("群聊会话序列号不存在，返回默认值0 - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId);
                return 0L;
            }
        } catch (Exception e) {
            System.err.println("获取群聊会话序列号失败 - 用户ID: " + userId +
                             ", 会话ID: " + conversationId + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return 0L;
        }
        // {{END MODIFICATIONS}}
    }

    /**
     * 更新单个群聊会话的最后序列号
     * 更新Redis中指定用户在指定群聊会话中的最后序列号，确保seq单调递增
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param newSeq 新序列号
     */
    public void updateConversationLastSeq(String userId, String conversationId, long newSeq) {
        // {{CHENGQI:
        // Action: Modified; Timestamp: 2025-08-06 21:32:28 +08:00; Reason: 实现更新Redis中单个群聊会话最后序列号的方法，确保seq单调递增;
        // }}
        // {{START MODIFICATIONS}}
        try (Jedis jedis = jedisPool.getResource()) {
            String key = conversationSeqPrefix + userId;

            // 获取当前已存储的seq
            String currentSeqStr = jedis.hget(key, conversationId);
            Long currentSeq = 0L;

            if (currentSeqStr != null && !currentSeqStr.isEmpty()) {
                try {
                    currentSeq = Long.parseLong(currentSeqStr);
                } catch (NumberFormatException e) {
                    System.err.println("解析当前seq失败，使用默认值0 - 用户ID: " + userId +
                                     ", 会话ID: " + conversationId + ", 当前值: " + currentSeqStr);
                    currentSeq = 0L;
                }
            }

            // 只有新seq大于当前seq时才更新
            if (newSeq > currentSeq) {
                jedis.hset(key, conversationId, String.valueOf(newSeq));
                jedis.expire(key, (int) RedisConfig.getClientConversationSeqExpire());

                System.out.println("更新群聊会话序列号成功 - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId +
                                 ", 当前seq: " + currentSeq + " -> 新seq: " + newSeq);
            } else {
                System.out.println("跳过seq更新（新seq不大于当前seq） - 用户ID: " + userId +
                                 ", 会话ID: " + conversationId +
                                 ", 当前seq: " + currentSeq + ", 新seq: " + newSeq);
            }

        } catch (Exception e) {
            System.err.println("更新群聊会话序列号失败 - 用户ID: " + userId +
                             ", 会话ID: " + conversationId + ", 序列号: " + newSeq + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
        // {{END MODIFICATIONS}}
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
                jedis.expire(key, (int) RedisConfig.getClientConversationSeqExpire());

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
     * 清理用户的本地数据
     *
     * @param userId 用户ID
     */
    public void clearUserData(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 清理客户端存储相关的键
            String syncSeqKey = syncSeqPrefix + userId;

            // 清理会话级序列号（需要模糊匹配）
            Set<String> conversationSeqKeys = jedis.keys(conversationSeqPrefix + userId + ":*");

            // 删除用户级序列号
            jedis.del(syncSeqKey);

            // 删除所有会话级序列号
            if (!conversationSeqKeys.isEmpty()) {
                jedis.del(conversationSeqKeys.toArray(new String[0]));
            }

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
            // 统计Redis中的客户端存储数据
            Set<String> syncSeqKeys = jedis.keys(syncSeqPrefix + "*");
            Set<String> conversationSeqKeys = jedis.keys(conversationSeqPrefix + "*");

            return new StorageStats(0L, syncSeqKeys.size() + conversationSeqKeys.size());
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
