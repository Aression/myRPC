package alg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class mt01 {
    private final Map<String, List<Long>> keyDataTimestamps;
    private final long timeWindowMillis;
    private final int threshold;

    public mt01(long timeWindowMillis, int threshold) {
        this.keyDataTimestamps = new ConcurrentHashMap<>();
        this.timeWindowMillis = timeWindowMillis;
        this.threshold = threshold;
    }

    public boolean isHotKey(String keyData) {
        long currentTime = System.currentTimeMillis();
        List<Long> timestamps = keyDataTimestamps.computeIfAbsent(keyData, k -> new ArrayList<>());

        synchronized (timestamps) {
            // 清理过期时间戳
            Iterator<Long> iterator = timestamps.iterator();
            while (iterator.hasNext()) {
                Long timestamp = iterator.next();
                if (currentTime - timestamp > timeWindowMillis) {
                    iterator.remove();
                } else {
                    break;
                }
            }
            System.out.println("清理后 [" + keyData + "] 时间戳数量: " + timestamps.size());

            // 添加当前时间戳
            timestamps.add(currentTime);
            System.out.println("添加后 [" + keyData + "] 时间戳数量: " + timestamps.size());
        }

        boolean isHot = timestamps.size() > threshold;
        System.out.println("判断结果: " + isHot + " (阈值:" + threshold + ")");
        return isHot;
    }

    public void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        System.out.println("\n开始全局清理...");
        
        keyDataTimestamps.entrySet().removeIf(entry -> {
            List<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                int originalSize = timestamps.size();
                timestamps.removeIf(ts -> currentTime - ts > timeWindowMillis);
                
                if (timestamps.isEmpty()) {
                    System.out.println("移除空键: " + entry.getKey());
                    return true;
                } else {
                    System.out.printf("清理键 [%s] 从 %d 到 %d 条记录%n", 
                                    entry.getKey(), originalSize, timestamps.size());
                    return false;
                }
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        mt01 detector = new mt01(5000, 3);
        String key = "testKey";

        System.out.println("开始测试热点检测:");
        for (int i = 0; i < 5; i++) {
            System.out.println("\n第 " + (i+1) + " 次检测:");
            detector.isHotKey(key);
            Thread.sleep(1000);
        }

        System.out.println("\n触发全局清理:");
        detector.cleanupExpiredData();
    }
}