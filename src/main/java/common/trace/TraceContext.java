package common.trace;

import java.util.UUID;

/**
 * 链路追踪上下文
 */
public class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }
    
    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID.set(traceId);
        }
        return traceId;
    }
    
    public static void setSpanId(String spanId) {
        SPAN_ID.set(spanId);
    }
    
    public static String getSpanId() {
        String spanId = SPAN_ID.get();
        if (spanId == null) {
            spanId = generateSpanId();
            SPAN_ID.set(spanId);
        }
        return spanId;
    }
    
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
    }
    
    public static String generateNewSpanId(String parentSpanId) {
        return parentSpanId + "." + System.nanoTime();
    }
    
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private static String generateSpanId() {
        return String.valueOf(System.nanoTime());
    }
} 