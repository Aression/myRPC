package common.serializer;

import java.util.ServiceLoader;

public class SerializerFactory {
    public static Serializer getSerializerByCode(int code) {
        // SPI
        ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);
        
        for (Serializer serializer : serviceLoader) {
            if (serializer.getType() == code) {
                return serializer;
            }
        }
        return null; // 未找到对应的序列化器，返回null
    }
}
