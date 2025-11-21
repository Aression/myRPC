package common.serializer.impl;

import common.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Java 原生序列化实现
 */
public class ObjectSerializer implements Serializer {
    private static final Logger logger = LoggerFactory.getLogger(ObjectSerializer.class);

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            logger.error("Java序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Java反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public int getType() {
        return 0; // Java序列化方式
    }

    @Override
    public String getSerializerName() {
        return "ObjectSerializer";
    }
}
