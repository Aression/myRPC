package common;

import common.message.MessageType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import common.serializer.impl.JsonSerializer;
import common.serializer.impl.ObjectSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

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
 * 
 * 3. 数据长度(4字节):
 *    - 标识序列化后的数据长度
 * 
 * 4. 数据(N字节):
 *    - 序列化后的消息体数据
 */

public class Encoder extends MessageToByteEncoder<Object> {
    private Serializer serializer;

    public Encoder(int serializerType){
        switch (serializerType) {
            case 0:
                this.serializer = new ObjectSerializer();
                break;
            case 1:
                this.serializer = new JsonSerializer();
                break;
            default:
                // 默认采用json序列化
                this.serializer = new JsonSerializer();
                break;
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        try {
            System.out.println("CORE-ENCODER: 开始编码消息: " + msg.getClass().getName());
            
            // 校验消息类型并写入头部
            if(msg instanceof RpcRequest) {
                out.writeShort(MessageType.REQUEST.getCode());
                System.out.println("CORE-ENCODER: 消息类型为请求");
            } else if(msg instanceof RpcResponse) {
                out.writeShort(MessageType.RESPONSE.getCode());
                System.out.println("CORE-ENCODER: 消息类型为响应");
            } else {
                throw new RuntimeException("CORE-ENCODER: 不支持的消息类型: " + msg.getClass().getName());
            }

            // 写入序列化器类型
            out.writeShort(serializer.getType());
            System.out.println("CORE-ENCODER: 使用序列化器类型: " + serializer.getSerializerName());

            // 序列化消息并写入
            byte[] serializedBytes = serializer.serialize(msg);
            out.writeInt(serializedBytes.length);
            out.writeBytes(serializedBytes);
            System.out.println("CORE-ENCODER: 消息编码完成，总长度: " + (4 + serializedBytes.length) + " 字节");
        } catch (Exception e) {
            System.out.println("CORE-ENCODER: 编码失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
