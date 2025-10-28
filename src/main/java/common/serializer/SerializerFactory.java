package common.serializer;

import common.serializer.impl.JsonSerializer;
import common.serializer.impl.ProtobufSerializer;
import common.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.ServiceLoader;

public class SerializerFactory {
    private static final Logger logger = LoggerFactory.getLogger(SerializerFactory.class);
    
    public static Serializer getSerializerByCode(int code) {
        try {
            logger.info("开始加载序列化器，类型: {}", code);
            
            // 统一配置：rpc.serializer.type 支持名称/数字
            String conf = AppConfig.getString("rpc.serializer.type", null);
            if (conf != null && !conf.isEmpty()) {
                Integer parsed = parseSerializerCode(conf);
                if (parsed != null) code = parsed;
            }

            // 系统属性覆盖：允许通过 -Dserializer.type=code 指定序列化实现
            String override = System.getProperty("serializer.type");
            if (override != null && !override.isEmpty()) {
                try {
                    int overrideCode = Integer.parseInt(override.trim());
                    code = overrideCode;
                    logger.info("检测到系统属性 serializer.type 覆盖，使用类型: {}", code);
                } catch (NumberFormatException ignore) {
                    logger.warn("serializer.type 非法，忽略: {}", override);
                }
            }

            // SPI
            ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);
            
            for (Serializer serializer : serviceLoader) {
                logger.debug("检查序列化器: {}, 类型: {}", serializer.getClass().getName(), serializer.getType());
                if (serializer.getType() == code) {
                    logger.info("找到匹配的序列化器: {}, 类型: {}", serializer.getClass().getName(), code);
                    return serializer;
                }
            }
            
            // 明确默认：优先 Protobuf（如已注册），否则 JSON
            for (Serializer serializer : ServiceLoader.load(Serializer.class)) {
                if (serializer instanceof ProtobufSerializer) {
                    logger.warn("未找到匹配的类型 {}，回退到默认 ProtobufSerializer", code);
                    return serializer;
                }
            }
            logger.warn("未找到匹配的类型 {}，回退到默认 JsonSerializer", code);
            return new JsonSerializer();
        } catch (Exception e) {
            logger.error("加载序列化器时发生异常，将使用默认的 JsonSerializer。错误: {}", e.getMessage(), e);
            return new JsonSerializer();
        }
    }

    private static Integer parseSerializerCode(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ignore) {
            String s = v.trim().toLowerCase(Locale.ROOT);
            switch (s) {
                case "object": return 0;
                case "json": return 1;
                case "protobuf": return 2;
                default: return null;
            }
        }
    }
}
