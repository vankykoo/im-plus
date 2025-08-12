package com.vanky.im.sequence.constant;

/**
 * 序列号服务常量定义
 * 
 * @author vanky
 * @since 2025-08-11
 */
public class SequenceConstants {
    
    /**
     * 序列号类型
     */
    public static class SequenceType {
        /** 用户级全局序列号 */
        public static final String USER_SEQ = "USER_SEQ";
        /** 会话级序列号 */
        public static final String CONVERSATION_SEQ = "CONVERSATION_SEQ";
    }
    
    /**
     * Redis相关常量
     */
    public static class Redis {
        /** Redis Key前缀 */
        public static final String KEY_PREFIX = "seq:section:";
        /** Hash字段：当前序列号 */
        public static final String FIELD_CUR_SEQ = "cur_seq";
        /** Hash字段：最大序列号 */
        public static final String FIELD_MAX_SEQ = "max_seq";
        /** 默认过期时间（7天） */
        public static final int DEFAULT_EXPIRE_SECONDS = 604800;
    }
    
    /**
     * 分段相关常量
     */
    public static class Section {
        /** 默认分段数量 */
        public static final int DEFAULT_SECTION_COUNT = 1024;
        /** 默认步长 */
        public static final int DEFAULT_STEP_SIZE = 10000;
        /** 用户分段前缀 */
        public static final String USER_SECTION_PREFIX = "user_";
        /** 会话分段前缀 */
        public static final String CONVERSATION_SECTION_PREFIX = "conversation_";
    }
    
    /**
     * Lua脚本操作结果
     */
    public static class LuaResult {
        /** 需要持久化 */
        public static final String PERSIST = "PERSIST";
        /** 无需操作 */
        public static final String NOP = "NOP";
    }
    
    /**
     * API响应状态
     */
    public static class ApiStatus {
        /** 成功 */
        public static final String SUCCESS = "SUCCESS";
        /** 失败 */
        public static final String FAILED = "FAILED";
        /** 错误 */
        public static final String ERROR = "ERROR";
    }
    
    /**
     * 异步持久化相关常量
     */
    public static class Persistence {
        /** 默认核心线程数 */
        public static final int DEFAULT_CORE_POOL_SIZE = 2;
        /** 默认最大线程数 */
        public static final int DEFAULT_MAX_POOL_SIZE = 4;
        /** 默认队列容量 */
        public static final int DEFAULT_QUEUE_CAPACITY = 1000;
        /** 线程名前缀 */
        public static final String THREAD_NAME_PREFIX = "sequence-persist-";
        /** 最大重试次数 */
        public static final int MAX_RETRY_TIMES = 3;
        /** 重试间隔基数（毫秒） */
        public static final long RETRY_INTERVAL_BASE = 1000L;
    }
}
