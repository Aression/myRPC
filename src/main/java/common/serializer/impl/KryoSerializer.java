package common.serializer.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo 序列化实现
 * 高性能，体积小
 */
public class KryoSerializer implements Serializer {
    private static final Logger logger = LoggerFactory.getLogger(KryoSerializer.class);

    // Kryo 不是线程安全的，需要使用 ThreadLocal
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 注册常用类，虽然不注册也能工作（会写入类名），但注册后性能更好体积更小
        // 这里为了通用性，暂时不强制注册所有类，开启 registrationRequired = false
        kryo.setRegistrationRequired(false);
        kryo.register(RpcRequest.class);
        kryo.register(RpcResponse.class);
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                Output output = new Output(bos)) {
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeClassAndObject(output, obj);
            output.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error("Kryo序列化失败", e);
            throw new RuntimeException("Kryo序列化失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                Input input = new Input(bis)) {
            Kryo kryo = kryoThreadLocal.get();
            return kryo.readClassAndObject(input);
        } catch (Exception e) {
            logger.error("Kryo反序列化失败", e);
            throw new RuntimeException("Kryo反序列化失败", e);
        }
    }

    @Override
    public int getType() {
        return 3; // Kryo 序列化方式 code = 3
    }

    @Override
    public String getSerializerName() {
        return "KryoSerializer";
    }
}
