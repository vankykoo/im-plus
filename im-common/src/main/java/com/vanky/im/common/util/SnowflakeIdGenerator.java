package com.vanky.im.common.util;

/**
 * 雪花算法ID生成器
 * 结构：时间戳(41位) + 数据中心ID(5位) + 机器ID(5位) + 序列号(12位)
 * 总共64位，生成的ID为long类型
 * 
 * 各部分说明：
 * 1. 符号位：1位，固定为0，保证生成的ID为正数
 * 2. 时间戳：41位，精确到毫秒，可使用约69年
 * 3. 数据中心ID：5位，最多支持32个数据中心
 * 4. 机器ID：5位，每个数据中心最多支持32台机器
 * 5. 序列号：12位，每毫秒可产生4096个ID
 */
public class SnowflakeIdGenerator {

    // 开始时间戳：2022-01-01 00:00:00
    private static final long START_TIMESTAMP = 1748611200000L;
    
    // 各部分占用位数
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MACHINE_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    // 各部分最大值
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);  // 31
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);       // 31
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);           // 4095
    
    // 各部分向左的位移
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;                              // 12
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;         // 17
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;  // 22
    
    private final long datacenterId;
    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    private static volatile SnowflakeIdGenerator instance;
    
    /**
     * 构造函数
     * @param datacenterId 数据中心ID (0-31)
     * @param machineId 机器ID (0-31)
     */
    private SnowflakeIdGenerator(long datacenterId, long machineId) {
        // 参数校验
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("数据中心ID必须在0到" + MAX_DATACENTER_ID + "之间");
        }
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("机器ID必须在0到" + MAX_MACHINE_ID + "之间");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }
    
    /**
     * 获取单例实例（使用默认的数据中心ID和机器ID）
     * @return SnowflakeIdGenerator实例
     */
    public static SnowflakeIdGenerator getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    instance = new SnowflakeIdGenerator(1L, 1L);
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取单例实例（使用指定的数据中心ID和机器ID）
     * 注意：应该在应用启动时调用此方法，且只调用一次
     * @param datacenterId 数据中心ID
     * @param machineId 机器ID
     * @return SnowflakeIdGenerator实例
     */
    public static SnowflakeIdGenerator init(long datacenterId, long machineId) {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    instance = new SnowflakeIdGenerator(datacenterId, machineId);
                }
            }
        }
        return instance;
    }
    
    /**
     * 生成下一个ID
     * @return 生成的ID
     */
    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimestamp();
        
        // 如果当前时间小于上一次ID生成的时间，说明系统时钟回退，抛出异常
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("系统时钟回退，拒绝生成ID。回退时间: %d毫秒", lastTimestamp - currentTimestamp));
        }
        
        // 如果是同一毫秒内生成的，则自增序列号
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 如果序列号用完，则等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            // 如果是新的一毫秒，则重置序列号
            sequence = 0L;
        }
        
        // 更新上次生成ID的时间戳
        lastTimestamp = currentTimestamp;
        
        // 组装ID并返回：时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }
    
    /**
     * 生成下一个ID并返回字符串格式
     * @return 字符串格式的ID
     */
    public String nextIdString() {
        return String.valueOf(nextId());
    }
    
    /**
     * 解析ID，获取其中的时间戳、数据中心ID、机器ID和序列号
     * @param id 要解析的ID
     * @return 包含各部分信息的字符串
     */
    public static String parseId(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
        long datacenterId = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long machineId = (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
        long sequence = id & MAX_SEQUENCE;
        
        return String.format("时间戳: %d (%s), 数据中心ID: %d, 机器ID: %d, 序列号: %d",
                timestamp, new java.util.Date(timestamp), datacenterId, machineId, sequence);
    }
    
    /**
     * 等待下一毫秒
     * @param lastTimestamp 上次生成ID的时间戳
     * @return 下一毫秒的时间戳
     */
    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
    
    /**
     * 获取当前时间戳
     * @return 当前时间戳（毫秒）
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * 获取数据中心ID
     * @return 数据中心ID
     */
    public long getDatacenterId() {
        return datacenterId;
    }
    
    /**
     * 获取机器ID
     * @return 机器ID
     */
    public long getMachineId() {
        return machineId;
    }
}