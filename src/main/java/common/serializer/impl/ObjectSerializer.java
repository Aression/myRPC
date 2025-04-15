package common.serializer.impl;

import common.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class ObjectSerializer implements Serializer {
    private static final Logger logger = LoggerFactory.getLogger(ObjectSerializer.class);

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            logger.error("Error in Object Serializer: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error in Object Deserializer: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public String getSerializerName() {
        return "ObjectSerializer";
    }
}
