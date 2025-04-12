package alg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class HotDataDetectorTimeWindow {
    private final Map<String, Deque<Long>> accessTimestamps = new ConcurrentHashMap<>();
    private final long windowSizeMillis = 60000; // 1分钟的时间窗口
    private final int threshold = 50; // 热点数据的访问阈值

    public void recordAccess(String dataId) {
        long currentTime = System.currentTimeMillis();
        accessTimestamps.computeIfAbsent(dataId, k -> new LinkedList<>()).addLast(currentTime);
    }

    // 获取热门数据
    public Set<String> getHotData() {
        // 获取当前时间
        long currentTime = System.currentTimeMillis();
        // 创建一个空的集合，用于存储热门数据
        Set<String> hotData = new HashSet<>();

        // 遍历访问时间戳的集合
        for (Map.Entry<String, Deque<Long>> entry : accessTimestamps.entrySet()) {
            // 获取当前键对应的访问时间戳队列
            Deque<Long> timestamps = entry.getValue();
            // 清理过期的时间戳
            while (!timestamps.isEmpty() && (currentTime - timestamps.peekFirst() > windowSizeMillis)) {
                timestamps.pollFirst();
            }
            // 检查访问次数是否超过阈值
            if (timestamps.size() > threshold) {
                // 如果超过阈值，将当前键添加到热门数据集合中
                hotData.add(entry.getKey());
            }
        }
        // 返回热门数据集合
        return hotData;
    }

    public static void main(String[] args) throws InterruptedException {
        HotDataDetectorTimeWindow detector = new HotDataDetectorTimeWindow();

        // 创建一个线程池，模拟多线程并发访问
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        // 模拟多线程并发访问
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 50; j++) {
                    detector.recordAccess("data" + (j % 5)); // 模拟访问多个数据键
                    try {
                        Thread.sleep(50); // 模拟访问间隔
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // 关闭线程池
        executorService.shutdown();

        // 等待线程池任务完成
        while (!executorService.isTerminated()) {
            Thread.sleep(100);
        }

        // 获取热门数据
        Set<String> hotData = detector.getHotData();
        System.out.println("热门数据: " + hotData);
    }
}