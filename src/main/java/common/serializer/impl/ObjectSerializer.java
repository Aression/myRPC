package common.serializer.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import common.serializer.Serializer;

public class ObjectSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj){
        byte[] bytes = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // 创建对象输出流，用于将对象写入字节数组输出流
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            // 将对象写入输出流
            oos.writeObject(obj);
            // 刷新缓冲区,确保所有数据都写入底层输出流
            oos.flush();
            // 将缓冲区内数据转为字节数组
            bytes = bos.toByteArray();
            // 关闭对象输出流
            oos.close();
            // 关闭字节数组输出流
            bos.close();
        } catch (Exception e) {
            if(e instanceof IOException){
                System.out.println("Error in Object Serializer: " + e);
            }
        }
        return bytes;
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        Object object = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            object = ois.readObject();
            ois.close();
            bis.close();
        } catch (Exception e) {
            if(e instanceof IOException){
                System.out.println("Error in Object Deserializer: " + e);
            }
            if(e instanceof ClassNotFoundException){
                System.out.println("Class not found in Object Deserializer: " + e); 
            }
        }
        return object;
    }

    @Override
    public int getType() {
        return 0; // 原生序列化器
    }

    @Override
    public String getSerializerName() {
        return "ObjectSerializer";
    }
}
