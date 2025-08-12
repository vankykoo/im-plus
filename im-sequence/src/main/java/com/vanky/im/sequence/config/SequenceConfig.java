package com.vanky.im.sequence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 序列号服务配置类
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sequence")
public class SequenceConfig {

    /**
     * 分段配置
     */
    private Section section = new Section();

    /**
     * Redis配置
     */
    private Redis redis = new Redis();

    /**
     * 异步持久化配置
     */
    private Persistence persistence = new Persistence();

    /**
     * 性能监控配置
     */
    private Monitor monitor = new Monitor();

    @Data
    public static class Section {
        /**
         * 分段数量
         */
        private int count = 1024;

        /**
         * 每个分段的步长
         */
        private int stepSize = 10000;
    }

    @Data
    public static class Redis {
        /**
         * Redis Key前缀
         */
        private String keyPrefix = "seq:section:";

        /**
         * 过期时间（秒）
         */
        private int expireSeconds = 604800; // 7天
    }

    @Data
    public static class Persistence {
        /**
         * 是否启用异步持久化
         */
        private boolean enabled = true;

        /**
         * 核心线程数
         */
        private int corePoolSize = 2;

        /**
         * 最大线程数
         */
        private int maxPoolSize = 4;

        /**
         * 队列容量
         */
        private int queueCapacity = 1000;

        /**
         * 线程名前缀
         */
        private String threadNamePrefix = "sequence-persist-";
    }

    @Data
    public static class Monitor {
        /**
         * 是否启用性能监控
         */
        private boolean enabled = true;

        /**
         * 统计间隔（秒）
         */
        private int statsIntervalSeconds = 60;
    }
}
