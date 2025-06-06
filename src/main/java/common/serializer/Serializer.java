package common.serializer;

public interface Serializer {
    // 把对象序列化成字节数组
    byte[] serialize(Object obj);
    // 从字节数组反序列化成消息, 使用java自带序列化方式不用messageType也能得到相应的对象（序列化字节数组里包含类信息）
    // 其它方式需指定消息格式，再根据message转化成相应的对象
    Object deserialize(byte[] bytes, int messageType);

    // 返回使用的序列器，是哪个
    // 0：java自带序列化方式, 1: json序列化方式
    int getType();
    
    String getSerializerName();
}
