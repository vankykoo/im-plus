package com.vanky.im.message.mq;

/**
 * 在单个消息消费线程中持有处理开始时间
 */
public class MessageProcessingTimeHolder {

    private static final ThreadLocal<Long> START_TIME_HOLDER = new ThreadLocal<>();

    public static void setStartTime(long startTime) {
        START_TIME_HOLDER.set(startTime);
    }

    public static Long getStartTime() {
        return START_TIME_HOLDER.get();
    }

    public static void clear() {
        START_TIME_HOLDER.remove();
    }
}