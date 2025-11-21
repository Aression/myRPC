package common.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始消息封装，用于延迟反序列化
 * 保存了从网络读取的原始字节和元数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RawMessage {
    private short messageType;
    private short serializerType;
    private byte[] data;
}
