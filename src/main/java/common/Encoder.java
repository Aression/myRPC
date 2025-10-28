package common;

import common.message.MessageType;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import common.serializer.SerializerFactory;
import common.serializer.impl.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义编码器，用于将对象编码为网络传输的字节流
 * 
 * <pre>
 * 协议格式:
 * +----------------------------------------------+
 * | 消息类型 | 序列化类型 | 数据长度 |    数据     |
 * |  2字节   |   2字节    |  4字节   |  N字节     |
 * +----------------------------------------------+
 * </pre>
 * 
 * 字段说明：
 * 1. 消息类型(2字节):
 *    - 0: 心跳请求
 *    - 1: 心跳响应 
 *    - 2: RPC请求
 *    - 3: RPC响应
 * 
 * 2. 序列化类型(2字节):
 *    - 0: Java序列化
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

    public Encoder(int serializerType) {
        try {
            logger.debug("初始化编码器，指定序列化类型: {}", serializerType);
            // 使用SPI机制加载序列化器
            this.serializer = SerializerFactory.getSerializerByCode(serializerType);
            
            // 如果没有找到对应的序列化器，使用默认的JSON序列化器
            if (this.serializer == null) {
                logger.warn("未识别的序列化方式, 默认采用json序列化。");
                this.serializer = new JsonSerializer();
            }
            
            logger.debug("成功初始化编码器，使用序列化器: {}", this.serializer.getClass().getName());
        } catch (Exception e) {
            logger.error("初始化编码器时发生异常: {}", e.getMessage(), e);
            logger.warn("将使用默认的JSON序列化器");
            this.serializer = new JsonSerializer();
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        logger.debug("CORE-ENCODER: 开始编码消息: {}", msg.getClass().getName());
        
        try {
            // 确定消息类型
            int messageType = 0;
            if (msg instanceof RpcRequest) {
                messageType = MessageType.REQUEST.getCode();
                logger.debug("CORE-ENCODER: 消息类型为请求");
            } else if (msg instanceof RpcResponse) {
                messageType = MessageType.RESPONSE.getCode();
                logger.debug("CORE-ENCODER: 消息类型为响应");
            } else {
                logger.error("CORE-ENCODER: 不支持的消息类型: {}", msg.getClass().getName());
                throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass().getName());
            }
            
            // 确保序列化器有效
            if (serializer == null) {
                logger.warn("序列化器为空，使用默认的JSON序列化器");
                serializer = new JsonSerializer();
            }
            
            // 序列化消息
            logger.debug("CORE-ENCODER: 使用序列化器类型: {}", serializer.getClass().getSimpleName());
            byte[] serializedBytes = serializer.serialize(msg);
            
            if (serializedBytes == null) {
                logger.error("序列化失败，消息将不会被发送");
                return;
            }
            
            // 写入消息类型(2字节)
            out.writeShort(messageType);
            
            // 写入序列化类型(2字节)
            int serializerType = serializer.getType();
            out.writeShort(serializerType);
            
            // 写入数据长度(4字节)
            out.writeInt(serializedBytes.length);
            
            // 写入数据(N字节)
            out.writeBytes(serializedBytes);
            
            logger.debug("CORE-ENCODER: 消息编码完成，总长度: {} 字节", 8 + serializedBytes.length);
        } catch (Exception e) {
            logger.error("CORE-ENCODER: 编码消息时发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }
}
