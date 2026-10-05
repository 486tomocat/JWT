package com.example.utils;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器。
 *
 * 位结构（共 64 位）：1 位符号(恒为 0) + 41 位时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列号。
 * 特点：全局唯一、趋势递增（对数据库索引友好）、纯数字、长度固定。
 *
 * 单机只有一个实例，dataCenterId / workerId 固定为 1/1；
 * 将来多实例部署时要把这两个值改成从配置或机器标识读取，否则不同实例会撞 ID。
 */
@Component
public class SnowflakeIdUtils {

    /** 起始时间戳（2023-08-04），41 位时间戳能用的年限就是从它开始算的 */
    private static final long START_TIMESTAMP = 1691087910202L;

    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private final long dataCenterId = 1L;
    private final long workerId = 1L;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdUtils() {
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("dataCenterId 超出范围: " + dataCenterId);
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 超出范围: " + workerId);
        }
    }

    /**
     * 生成下一个 ID。
     * 加 synchronized：同一实例内必须串行，否则同一毫秒内两次调用会算出相同的 ID。
     *
     * @return 雪花 ID
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            // 时钟回拨：宁可报错，也不要生成可能重复的 ID
            throw new IllegalStateException(
                    "系统时钟回拨，拒绝生成 ID（回拨 " + (lastTimestamp - timestamp) + "ms）");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内：序列号自增，溢出就等到下一毫秒
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                do {
                    timestamp = System.currentTimeMillis();
                    Thread.onSpinWait();
                } while (timestamp <= lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }
}
