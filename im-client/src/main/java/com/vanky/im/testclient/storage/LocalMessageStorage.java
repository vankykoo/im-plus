package com.vanky.im.testclient.storage;

// Redis相关导入临时注释，网络问题导致无法下载依赖
// import redis.clients.jedis.Jedis;
// import redis.clients.jedis.JedisPool;
// import redis.clients.jedis.JedisPoolConfig;
// import com.vanky.im.testclient.config.RedisConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.io.*;
import java.util.Properties;

/**
 * 本地消息存储管理器（临时回退到文件存储）
 * 负责管理客户端的本地消息存储和同步状态
 * 注意：由于网络问题无法下载Redis依赖，临时回退到文件存储
 *
 * @author vanky
 * @create 2025/7/29
 * @update 2025/7/30 - 临时回退到文件存储
 */
// {{CHENGQI:
// Action: Reverted; Timestamp: 2025-07-30 21:26:00 +08:00; Reason: 网络问题无法下载Jedis依赖，临时回退到文件存储;
// }}
// {{START MODIFICATIONS}}
public class LocalMessageStorage {

    private static final String STORAGE_DIR = "im-client-data";
    private static final String SYNC_STATE_FILE = "sync-state.properties";

    // 内存缓存，提高读取性能
    private final ConcurrentMap<String, Long> syncSeqCache = new ConcurrentHashMap<>();

    // 同步状态文件
    private final File syncStateFile;
    private final Properties syncStateProps;

    public LocalMessageStorage() {
        // 初始化存储目录
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        // 初始化同步状态文件
        this.syncStateFile = new File(storageDir, SYNC_STATE_FILE);
        this.syncStateProps = new Properties();

        loadSyncState();

        System.out.println("LocalMessageStorage初始化完成 - 使用文件存储 (临时回退)");
    }

    /**
     * 获取用户的最后同步序列号
     *
     * @param userId 用户ID
     * @return 最后同步序列号，如果用户首次同步则返回0
     */
    public Long getLastSyncSeq(String userId) {
        // 优先从内存缓存获取
        Long cachedSeq = syncSeqCache.get(userId);
        if (cachedSeq != null) {
            return cachedSeq;
        }

        // 从配置文件获取
        String key = "user." + userId + ".lastSyncSeq";
        String value = syncStateProps.getProperty(key, "0");

        try {
            Long seq = Long.parseLong(value);
            syncSeqCache.put(userId, seq); // 缓存到内存
            return seq;
        } catch (NumberFormatException e) {
            System.err.println("解析同步序列号失败，使用默认值0 - 用户ID: " + userId + ", 值: " + value);
            return 0L;
        }
    }

    /**
     * 更新用户的最后同步序列号
     *
     * @param userId 用户ID
     * @param lastSyncSeq 最后同步序列号
     */
    public void updateLastSyncSeq(String userId, Long lastSyncSeq) {
        try {
            // 更新内存缓存
            syncSeqCache.put(userId, lastSyncSeq);

            // 更新配置文件
            String key = "user." + userId + ".lastSyncSeq";
            syncStateProps.setProperty(key, lastSyncSeq.toString());

            // 持久化到文件
            saveSyncState();

            System.out.println("更新同步序列号成功 - 用户ID: " + userId + ", 序列号: " + lastSyncSeq);

        } catch (Exception e) {
            System.err.println("更新同步序列号失败 - 用户ID: " + userId + ", 序列号: " + lastSyncSeq);
            e.printStackTrace();
        }
    }

    /**
     * 批量存储消息到本地数据库
     * 注意：这是一个简化实现，实际项目中应该使用SQLite等数据库
     *
     * @param userId 用户ID
     * @param messages 消息列表（JSON格式）
     * @return 存储是否成功
     */
    public boolean storeMessages(String userId, String messages) {
        try {
            // 创建用户消息目录
            File userDir = new File(STORAGE_DIR, "messages/" + userId);
            if (!userDir.exists()) {
                userDir.mkdirs();
            }

            // 生成消息文件名（基于时间戳）
            String fileName = "messages_" + System.currentTimeMillis() + ".json";
            File messageFile = new File(userDir, fileName);

            // 写入消息内容
            try (FileWriter writer = new FileWriter(messageFile, true)) {
                writer.write(messages);
                writer.write("\n");
            }

            System.out.println("消息存储成功 - 用户ID: " + userId + ", 文件: " + fileName);
            return true;

        } catch (Exception e) {
            System.err.println("消息存储失败 - 用户ID: " + userId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取用户的本地消息数量（估算）
     *
     * @param userId 用户ID
     * @return 本地消息数量
     */
    public long getLocalMessageCount(String userId) {
        try {
            File userDir = new File(STORAGE_DIR, "messages/" + userId);
            if (!userDir.exists()) {
                return 0L;
            }

            File[] messageFiles = userDir.listFiles((dir, name) -> name.endsWith(".json"));
            return messageFiles != null ? messageFiles.length : 0L;

        } catch (Exception e) {
            System.err.println("获取本地消息数量失败 - 用户ID: " + userId);
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 清理用户的本地数据
     *
     * @param userId 用户ID
     */
    public void clearUserData(String userId) {
        try {
            // 清理内存缓存
            syncSeqCache.remove(userId);

            // 清理同步状态
            String key = "user." + userId + ".lastSyncSeq";
            syncStateProps.remove(key);
            saveSyncState();

            // 清理消息文件
            File userDir = new File(STORAGE_DIR, "messages/" + userId);
            if (userDir.exists()) {
                deleteDirectory(userDir);
            }

            System.out.println("用户数据清理完成 - 用户ID: " + userId);

        } catch (Exception e) {
            System.err.println("用户数据清理失败 - 用户ID: " + userId);
            e.printStackTrace();
        }
    }

    /**
     * 获取存储统计信息
     */
    public StorageStats getStorageStats() {
        try {
            File storageDir = new File(STORAGE_DIR);
            long totalSize = calculateDirectorySize(storageDir);
            int userCount = syncSeqCache.size();

            return new StorageStats(totalSize, userCount);

        } catch (Exception e) {
            System.err.println("获取存储统计信息失败");
            e.printStackTrace();
            return new StorageStats(0L, 0);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 加载同步状态
     */
    private void loadSyncState() {
        try {
            if (syncStateFile.exists()) {
                try (FileInputStream fis = new FileInputStream(syncStateFile)) {
                    syncStateProps.load(fis);
                }
                System.out.println("同步状态加载成功 - 文件: " + syncStateFile.getAbsolutePath());
            } else {
                System.out.println("同步状态文件不存在，将创建新文件");
            }
        } catch (Exception e) {
            System.err.println("加载同步状态失败");
            e.printStackTrace();
        }
    }

    /**
     * 保存同步状态
     */
    private void saveSyncState() {
        try {
            try (FileOutputStream fos = new FileOutputStream(syncStateFile)) {
                syncStateProps.store(fos, "IM Client Sync State - Updated at " + new java.util.Date());
            }
        } catch (Exception e) {
            System.err.println("保存同步状态失败");
            e.printStackTrace();
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    /**
     * 计算目录大小
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += calculateDirectorySize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        } else {
            size = directory.length();
        }
        return size;
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
            return String.format("StorageStats{totalSize=%d bytes, userCount=%d}", totalSize, userCount);
        }
    }
}
// {{END MODIFICATIONS}}
