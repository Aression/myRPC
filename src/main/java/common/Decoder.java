package common;

import java.util.List;

import javax.management.RuntimeErrorException;

import common.message.MessageType;
import common.serializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class Decoder extends ByteToMessageDecoder{

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        // 读取并判断消息类型是否合法
        short messageType = in.readShort();
        if(!MessageType.isValid(messageType)) {
            throw new RuntimeException("CORE-DECODER: 不支持的消息类型: " + messageType);
        }
        
        // 读取序列化方式和类型
        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByCode(serializerType);
        if(serializer==null) throw new RuntimeException("CORE-DECODER: 不存在对应序列化器：" + serializerType);
        
        // 读取序列化数组信息并执行解码
        int arrLen = in.readInt();
        byte[] bytes = new byte[arrLen];
        in.readBytes(bytes);
        Object deserialized = serializer.deserialize(bytes, messageType);
        out.add(deserialized);
    }
    
}
