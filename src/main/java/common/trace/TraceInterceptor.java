package common.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 链路追踪拦截器
 */
public class TraceInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TraceInterceptor.class);
    
    public static void clientBeforeRequest() {
        String traceId = TraceContext.getTraceId();
        String spanId = TraceContext.getSpanId();
        logger.info("Client Request - TraceId: {}, SpanId: {}", traceId, spanId);
    }
    
    public static void clientAfterResponse() {
        String traceId = TraceContext.getTraceId();
        String spanId = TraceContext.getSpanId();
        logger.info("Client Response - TraceId: {}, SpanId: {}", traceId, spanId);
        TraceContext.clear();
    }
    
    public static void serverBeforeHandle(String traceId, String parentSpanId) {
        TraceContext.setTraceId(traceId);
        String spanId = TraceContext.generateNewSpanId(parentSpanId);
        TraceContext.setSpanId(spanId);
        logger.info("Server Handle Start - TraceId: {}, ParentSpanId: {}, SpanId: {}", 
            traceId, parentSpanId, spanId);
    }
    
    public static void serverAfterHandle() {
        String traceId = TraceContext.getTraceId();
        String spanId = TraceContext.getSpanId();
        logger.info("Server Handle End - TraceId: {}, SpanId: {}", traceId, spanId);
        TraceContext.clear();
    }
} 