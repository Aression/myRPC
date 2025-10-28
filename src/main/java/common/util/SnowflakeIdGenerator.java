package common.util;

/**
 * 基于 Twitter Snowflake 的分布式唯一ID生成器。
 * 结构: 1bit 符号位 + 41bit 时间戳(毫秒) + 5bit 数据中心 + 5bit 机器ID + 12bit 序列
 */
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1577836800000L; // 2020-01-01T00:00:00Z

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private static volatile SnowflakeIdGenerator instance;

    public static SnowflakeIdGenerator getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    long dc = parseLongOrDefault(System.getProperty("snowflake.datacenterId"), 0L);
                    long wk = parseLongOrDefault(System.getProperty("snowflake.workerId"), 0L);
                    instance = new SnowflakeIdGenerator(dc, wk);
                }
            }
        }
        return instance;
    }

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0)
            throw new IllegalArgumentException("workerId out of range: " + workerId);
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0)
            throw new IllegalArgumentException("datacenterId out of range: " + datacenterId);
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            // 时钟回拨处理：简单等待直到超过 lastTimestamp
            long offset = lastTimestamp - timestamp;
            try {
                Thread.sleep(Math.min(offset, 5));
            } catch (InterruptedException ignored) { }
            timestamp = currentTime();
            if (timestamp < lastTimestamp) {
                // 仍回拨，降级为在 lastTimestamp 上自旋
                timestamp = lastTimestamp;
            }
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列溢出，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public String nextIdStr() {
        return Long.toUnsignedString(nextId());
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTime();
        while (ts <= lastTs) {
            ts = currentTime();
        }
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }

    private static long parseLongOrDefault(String value, long def) {
        if (value == null || value.isEmpty()) return def;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return def; }
    }
}


