package common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceTracker {
    public static boolean ENABLED = false;
    private static final Map<String, Map<String, Long>> TIMINGS = new ConcurrentHashMap<>();

    public static void startTracking(String requestId) {
        if (ENABLED) {
            TIMINGS.put(requestId, new ConcurrentHashMap<>());
            record(requestId, "start");
        }
    }

    public static void record(String requestId, String phase) {
        if (ENABLED) {
            Map<String, Long> map = TIMINGS.get(requestId);
            if (map != null) {
                map.put(phase, System.nanoTime());
            }
        }
    }

    public static Map<String, Long> getTimings(String requestId) {
        return TIMINGS.get(requestId);
    }

    public static Map<String, Map<String, Long>> getAllTimings() {
        return TIMINGS;
    }

    public static void clear(String requestId) {
        TIMINGS.remove(requestId);
    }

    public static void clearAll() {
        TIMINGS.clear();
    }
}
