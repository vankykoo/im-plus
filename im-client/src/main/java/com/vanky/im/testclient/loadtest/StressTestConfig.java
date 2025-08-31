package com.vanky.im.testclient.loadtest;

/**
 * 压测配置类
 * 遵循不可变对象设计模式，确保配置的线程安全性
 * 
 * @author vanky
 * @since 2025-08-29
 */
public class StressTestConfig {
    
    /**
     * 压测类型枚举
     */
    public enum TestType {
        PRIVATE_MESSAGE("私聊消息"),
        GROUP_MESSAGE("群聊消息");
        
        private final String description;
        
        TestType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final TestType testType;
    private final String targetId;          // 目标用户ID或群组ID
    private final int messageCount;         // 消息总数
    private final int concurrency;          // 并发数
    private final int sendIntervalMs;       // 发送间隔(毫秒)
    private final int timeoutSeconds;       // 超时时间(秒)
    private final String messageTemplate;   // 消息模板
    
    public StressTestConfig(TestType testType, String targetId, int messageCount, 
                           int concurrency, int sendIntervalMs, int timeoutSeconds, 
                           String messageTemplate) {
        this.testType = testType;
        this.targetId = targetId;
        this.messageCount = messageCount;
        this.concurrency = concurrency;
        this.sendIntervalMs = sendIntervalMs;
        this.timeoutSeconds = timeoutSeconds;
        this.messageTemplate = messageTemplate;
    }
    
    // 建造者模式，简化配置创建
    public static class Builder {
        private TestType testType = TestType.PRIVATE_MESSAGE;
        private String targetId = "";
        private int messageCount = 100;
        private int concurrency = 5;
        private int sendIntervalMs = 10;
        private int timeoutSeconds = 60;
        private String messageTemplate = "压测消息 #%d";
        
        public Builder testType(TestType testType) {
            this.testType = testType;
            return this;
        }
        
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }
        
        public Builder messageCount(int messageCount) {
            this.messageCount = messageCount;
            return this;
        }
        
        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }
        
        public Builder sendIntervalMs(int sendIntervalMs) {
            this.sendIntervalMs = sendIntervalMs;
            return this;
        }
        
        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }
        
        public Builder messageTemplate(String messageTemplate) {
            this.messageTemplate = messageTemplate;
            return this;
        }
        
        public StressTestConfig build() {
            validate();
            return new StressTestConfig(testType, targetId, messageCount, 
                                      concurrency, sendIntervalMs, timeoutSeconds, 
                                      messageTemplate);
        }
        
        private void validate() {
            if (targetId == null || targetId.trim().isEmpty()) {
                throw new IllegalArgumentException("目标ID不能为空");
            }
            if (messageCount <= 0) {
                throw new IllegalArgumentException("消息数量必须大于0");
            }
            if (concurrency <= 0) {
                throw new IllegalArgumentException("并发数必须大于0");
            }
            if (sendIntervalMs < 0) {
                throw new IllegalArgumentException("发送间隔不能为负数");
            }
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("超时时间必须大于0");
            }
            if (messageTemplate == null || messageTemplate.trim().isEmpty()) {
                throw new IllegalArgumentException("消息模板不能为空");
            }
        }
    }
    
    // Getters
    public TestType getTestType() { return testType; }
    public String getTargetId() { return targetId; }
    public int getMessageCount() { return messageCount; }
    public int getConcurrency() { return concurrency; }
    public int getSendIntervalMs() { return sendIntervalMs; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getMessageTemplate() { return messageTemplate; }
    
    @Override
    public String toString() {
        return String.format("StressTestConfig{类型=%s, 目标=%s, 消息数=%d, 并发=%d, 间隔=%dms, 超时=%ds}", 
                           testType.getDescription(), targetId, messageCount, 
                           concurrency, sendIntervalMs, timeoutSeconds);
    }
}
