package common;

import java.util.List;

import common.message.MessageType;
import common.message.RawMessage;
import common.serializer.Serializer;
import common.serializer.SerializerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class Decoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        // 读取并判断消息类型是否合法
        short messageType = in.readShort();
        if (!MessageType.isValid(messageType)) {
            throw new RuntimeException("CORE-DECODER: 不支持的消息类型: " + messageType);
        }

        // 读取序列化方式和类型
        short serializerType = in.readShort();
        // 校验序列化器是否存在（虽然不立即反序列化，但最好先校验一下）
        Serializer serializer = SerializerFactory.getSerializerByCode(serializerType);
        if (serializer == null)
            throw new RuntimeException("CORE-DECODER: 不存在对应序列化器：" + serializerType);

        // 读取序列化数组信息
        int arrLen = in.readInt();
        byte[] bytes = new byte[arrLen];
        in.readBytes(bytes);

        // 关键修改：不进行反序列化，直接封装为 RawMessage
        RawMessage rawMessage = new RawMessage(messageType, serializerType, bytes);
        out.add(rawMessage);
    }

}
