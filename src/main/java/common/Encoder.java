package common;

import common.message.MessageType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import common.serializer.impl.JsonSerializer;
import common.serializer.impl.ObjectSerializer;
import common.serializer.impl.ProtobufSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息结构如下:
 * +---------------+---------------+-----------------+-------------+
 * |  消息类型      |  序列化类型    |    数据长度      |    数据      |
 * |    2字节      |    2字节      |     4字节       |   N字节     |
 * +---------------+---------------+-----------------+-------------+
 * 
 * 1. 消息类型(2字节):
 *    - 0: RpcRequest请求消息
 *    - 1: RpcResponse响应消息
 * 
 * 2. 序列化类型(2字节):
 *    - 0: Java原生序列化
 *    - 1: JSON序列化
 *    - 2: Protobuf序列化
 * 
 * 3. 数据长度(4字节):
 *    - 标识序列化后的数据长度
 * 
 * 4. 数据(N字节):
 *    - 序列化后的消息体数据
 */

public class Encoder extends MessageToByteEncoder<Object> {
    private static final Logger logger = LoggerFactory.getLogger(Encoder.class);
    private Serializer serializer;

    public Encoder(int serializerType){
        switch (serializerType) {
            case 0:
                this.serializer = new ObjectSerializer();
                break;
            case 1:
                this.serializer = new JsonSerializer();
                break;
            case 2:
                this.serializer = new ProtobufSerializer();
                break;
                
            default:
                logger.warn("未识别的序列化方式, 默认采用json序列化。");
                this.serializer = new JsonSerializer();
                break;
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        logger.info("CORE-ENCODER: 开始编码消息: {}", msg.getClass().getName());
        
        // 确定消息类型
        int messageType = 0;
        if (msg instanceof RpcRequest) {
            messageType = MessageType.REQUEST.getCode();
            logger.info("CORE-ENCODER: 消息类型为请求");
        } else if (msg instanceof RpcResponse) {
            messageType = MessageType.RESPONSE.getCode();
            logger.info("CORE-ENCODER: 消息类型为响应");
        } else{
            logger.error("CORE-ENCODER: 不支持的消息类型: {}", msg.getClass().getName());
            throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass().getName());
        }

        // 序列化消息
        byte[] serializedBytes;
        if (serializer == null) {
            logger.warn("未识别的序列化方式, 默认采用json序列化。");
            serializer = new JsonSerializer();
        }
        logger.info("CORE-ENCODER: 使用序列化器类型: {}", serializer.getClass().getSimpleName());
        serializedBytes = serializer.serialize(msg);

        // 写入消息类型(2字节)
        out.writeShort(messageType);
        
        // 写入序列化类型(2字节)
        int serializerType = 0;
        if (serializer instanceof JsonSerializer) {
            serializerType = 1;
        } else if (serializer instanceof ProtobufSerializer) {
            serializerType = 2;
        }
        out.writeShort(serializerType);
        
        // 写入数据长度(4字节)
        out.writeInt(serializedBytes.length);
        
        // 写入数据(N字节)
        out.writeBytes(serializedBytes);
        
        logger.info("CORE-ENCODER: 消息编码完成，总长度: {} 字节", 8 + serializedBytes.length);
    }
}
