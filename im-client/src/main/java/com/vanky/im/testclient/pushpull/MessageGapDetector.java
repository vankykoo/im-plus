package com.vanky.im.testclient.pushpull;

import java.util.logging.Logger;

/**
 * 消息空洞检测器
 * 用于检测消息序列号中的空洞，触发拉取补偿机制
 *
 * @author vanky
 * @since 2025-08-05
 */
public class MessageGapDetector {

    private static final Logger log = Logger.getLogger(MessageGapDetector.class.getName());
    
    /**
     * 检测消息空洞
     * 
     * @param receivedSeq 接收到的消息序列号
     * @param localSeq 本地已确认的最大序列号
     * @return 是否检测到空洞
     */
    public boolean detectGap(long receivedSeq, long localSeq) {
        // 检查序列号的有效性
        if (receivedSeq <= 0 || localSeq < 0) {
            log.warning("无效的序列号 - 接收序列号: " + receivedSeq + ", 本地序列号: " + localSeq);
            return false;
        }

        // 如果接收到的序列号大于本地序列号+1，说明存在空洞
        boolean hasGap = receivedSeq > localSeq + 1;

        if (hasGap) {
            log.info("检测到消息空洞 - 接收序列号: " + receivedSeq + ", 本地序列号: " + localSeq +
                    ", 空洞范围: [" + (localSeq + 1) + ", " + (receivedSeq - 1) + "]");
        } else {
            log.info("消息序列连续 - 接收序列号: " + receivedSeq + ", 本地序列号: " + localSeq);
        }
        
        return hasGap;
    }
    
    /**
     * 获取空洞范围
     * 
     * @param receivedSeq 接收到的消息序列号
     * @param localSeq 本地已确认的最大序列号
     * @return 空洞范围，如果没有空洞则返回null
     */
    public GapRange getGapRange(long receivedSeq, long localSeq) {
        if (!detectGap(receivedSeq, localSeq)) {
            return null;
        }
        
        long fromSeq = localSeq + 1;
        long toSeq = receivedSeq - 1;
        
        GapRange gapRange = new GapRange(fromSeq, toSeq);
        log.info("计算空洞范围 - 起始序列号: " + fromSeq + ", 结束序列号: " + toSeq + ", 空洞大小: " + gapRange.getGapSize());

        return gapRange;
    }
    
    /**
     * 检查消息是否连续
     * 
     * @param receivedSeq 接收到的消息序列号
     * @param localSeq 本地已确认的最大序列号
     * @return 是否连续
     */
    public boolean isSequential(long receivedSeq, long localSeq) {
        return receivedSeq == localSeq + 1;
    }
    
    /**
     * 检查消息是否重复或过期
     * 
     * @param receivedSeq 接收到的消息序列号
     * @param localSeq 本地已确认的最大序列号
     * @return 是否重复或过期
     */
    public boolean isDuplicateOrExpired(long receivedSeq, long localSeq) {
        return receivedSeq <= localSeq;
    }
    
    /**
     * 获取消息状态
     * 
     * @param receivedSeq 接收到的消息序列号
     * @param localSeq 本地已确认的最大序列号
     * @return 消息状态
     */
    public MessageStatus getMessageStatus(long receivedSeq, long localSeq) {
        if (receivedSeq <= 0 || localSeq < 0) {
            return MessageStatus.INVALID;
        }
        
        if (receivedSeq <= localSeq) {
            return MessageStatus.DUPLICATE_OR_EXPIRED;
        } else if (receivedSeq == localSeq + 1) {
            return MessageStatus.SEQUENTIAL;
        } else {
            return MessageStatus.OUT_OF_ORDER;
        }
    }
    
    /**
     * 空洞范围
     */
    public static class GapRange {
        private final long fromSeq;
        private final long toSeq;
        
        public GapRange(long fromSeq, long toSeq) {
            this.fromSeq = fromSeq;
            this.toSeq = toSeq;
        }
        
        public long getFromSeq() {
            return fromSeq;
        }
        
        public long getToSeq() {
            return toSeq;
        }
        
        public long getGapSize() {
            return toSeq - fromSeq + 1;
        }
        
        public boolean isValid() {
            return fromSeq > 0 && toSeq >= fromSeq;
        }
        
        @Override
        public String toString() {
            return String.format("GapRange{fromSeq=%d, toSeq=%d, gapSize=%d}", 
                    fromSeq, toSeq, getGapSize());
        }
    }
    
    /**
     * 消息状态枚举
     */
    public enum MessageStatus {
        /** 无效消息（序列号无效） */
        INVALID,
        
        /** 重复或过期消息 */
        DUPLICATE_OR_EXPIRED,
        
        /** 连续消息（可以立即处理） */
        SEQUENTIAL,
        
        /** 乱序消息（需要缓存等待） */
        OUT_OF_ORDER
    }
}
