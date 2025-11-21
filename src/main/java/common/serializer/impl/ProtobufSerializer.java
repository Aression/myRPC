package common.serializer.impl;

import common.message.Rpc;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import com.alibaba.fastjson.JSON;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Protobuf序列化实现
 */
public class ProtobufSerializer implements Serializer {
    private static final Logger logger = LoggerFactory.getLogger(ProtobufSerializer.class);

    @Override
    public byte[] serialize(Object obj) {
        try {
            logger.debug("开始序列化对象: {}", obj.getClass().getName());

            if (obj instanceof RpcRequest) {
                return serializeRequest((RpcRequest) obj);
            } else if (obj instanceof RpcResponse) {
                return serializeResponse((RpcResponse) obj);
            } else if (obj instanceof MessageLite) {
                return ((MessageLite) obj).toByteArray();
            } else {
                throw new IllegalArgumentException("不支持的对象类型: " + obj.getClass().getName());
            }
        } catch (Exception e) {
            logger.error("序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private byte[] serializeRequest(RpcRequest request) {
        Rpc.RpcRequest.Builder builder = Rpc.RpcRequest.newBuilder();

        // 设置基本字段
        if (request.getRequestId() != null)
            builder.setRequestId(request.getRequestId());
        if (request.getInterfaceName() != null)
            builder.setInterfaceName(request.getInterfaceName());
        if (request.getMethodName() != null)
            builder.setMethodName(request.getMethodName());

        // 处理参数
        if (request.getParams() != null) {
            for (Object param : request.getParams()) {
                builder.addParams(
                        param != null ? (isSimpleType(param) ? param.toString() : JSON.toJSONString(param)) : "");
            }
        }

        // 处理参数类型
        if (request.getParamsType() != null) {
            for (Class<?> paramType : request.getParamsType()) {
                builder.addParamsType(paramType != null ? paramType.getName() : "");
            }
        }

        // 设置其他字段
        builder.setTimestamp(request.getTimestamp());
        if (request.getTraceId() != null)
            builder.setTraceId(request.getTraceId());
        if (request.getSpanId() != null)
            builder.setSpanId(request.getSpanId());

        return builder.build().toByteArray();
    }

    private byte[] serializeResponse(RpcResponse response) {
        Rpc.RpcResponse.Builder builder = Rpc.RpcResponse.newBuilder();

        // 设置基本字段
        if (response.getRequestId() != null)
            builder.setRequestId(response.getRequestId());
        if (response.getCode() != null)
            builder.setCode(response.getCode());
        builder.setMessage(response.getMessage() != null ? response.getMessage() : "");

        // 设置数据类型
        if (response.getDataType() != null) {
            builder.setDataType(response.getDataType().getName());
        } else {
            builder.setDataType("");
        }

        // 设置数据
        if (response.getData() != null) {
            builder.setData(isSimpleType(response.getData()) ? response.getData().toString()
                    : JSON.toJSONString(response.getData()));
        } else {
            builder.setData("");
        }

        // 设置其他字段
        if (response.getTraceId() != null)
            builder.setTraceId(response.getTraceId());
        if (response.getSpanId() != null)
            builder.setSpanId(response.getSpanId());

        return builder.build().toByteArray();
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        try {
            logger.debug("开始反序列化，消息类型: {}", messageType);

            switch (messageType) {
                case 0:
                    return deserializeRequest(bytes);
                case 1:
                    return deserializeResponse(bytes);
                default:
                    throw new IllegalArgumentException("不支持的消息类型: " + messageType);
            }
        } catch (InvalidProtocolBufferException e) {
            logger.error("反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private RpcRequest deserializeRequest(byte[] bytes) throws InvalidProtocolBufferException {
        Rpc.RpcRequest protoRequest = Rpc.RpcRequest.parseFrom(bytes);
        RpcRequest request = new RpcRequest();

        // 设置基本字段
        request.setRequestId(protoRequest.getRequestId());
        request.setInterfaceName(protoRequest.getInterfaceName());
        request.setMethodName(protoRequest.getMethodName());

        // 处理参数类型
        List<String> paramsTypeList = protoRequest.getParamsTypeList();
        Class<?>[] paramsTypes = new Class<?>[paramsTypeList.size()];
        for (int i = 0; i < paramsTypeList.size(); i++) {
            try {
                paramsTypes[i] = Class.forName(paramsTypeList.get(i));
            } catch (ClassNotFoundException e) {
                paramsTypes[i] = String.class;
            }
        }
        request.setParamsType(paramsTypes);

        // 处理参数
        List<String> paramsList = protoRequest.getParamsList();
        Object[] params = new Object[paramsList.size()];
        for (int i = 0; i < paramsList.size(); i++) {
            params[i] = convertParam(paramsList.get(i), paramsTypes[i]);
        }
        request.setParams(params);

        // 设置其他字段
        request.setTimestamp(protoRequest.getTimestamp());
        request.setTraceId(protoRequest.getTraceId());
        request.setSpanId(protoRequest.getSpanId());

        return request;
    }

    private RpcResponse deserializeResponse(byte[] bytes) throws InvalidProtocolBufferException {
        Rpc.RpcResponse protoResponse = Rpc.RpcResponse.parseFrom(bytes);
        RpcResponse response = new RpcResponse();

        // 设置基本字段
        response.setRequestId(protoResponse.getRequestId());
        response.setCode(protoResponse.getCode());
        response.setMessage(protoResponse.getMessage());

        // 处理数据类型和数据
        String dataTypeStr = protoResponse.getDataType();
        if (dataTypeStr != null && !dataTypeStr.isEmpty()) {
            try {
                Class<?> dataType = Class.forName(dataTypeStr);
                response.setDataType(dataType);

                String dataStr = protoResponse.getData();
                if (dataStr != null && !dataStr.isEmpty()) {
                    response.setData(convertParam(dataStr, dataType));
                }
            } catch (ClassNotFoundException e) {
                logger.error("找不到数据类型: {}", e.getMessage());
                response.setDataType(String.class);
                response.setData(protoResponse.getData());
            }
        }

        // 设置其他字段
        response.setTraceId(protoResponse.getTraceId());
        response.setSpanId(protoResponse.getSpanId());

        return response;
    }

    private Object convertParam(String paramStr, Class<?> paramType) {
        try {
            if (paramType == String.class) {
                return paramStr;
            } else if (paramType == Integer.class || paramType == int.class) {
                return Integer.parseInt(paramStr);
            } else if (paramType == Boolean.class || paramType == boolean.class) {
                return Boolean.parseBoolean(paramStr);
            } else if (paramType == Double.class || paramType == double.class) {
                return Double.parseDouble(paramStr);
            } else if (paramType == Float.class || paramType == float.class) {
                return Float.parseFloat(paramStr);
            } else if (paramType == Long.class || paramType == long.class) {
                return Long.parseLong(paramStr);
            } else {
                // 对于复杂对象，使用JSON反序列化
                return JSON.parseObject(paramStr, paramType);
            }
        } catch (Exception e) {
            logger.error("参数转换失败: {}", e.getMessage());
            return getDefaultValue(paramType);
        }
    }

    private Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == int.class || type == long.class ||
                    type == short.class || type == byte.class) {
                return 0;
            } else if (type == float.class || type == double.class) {
                return 0.0;
            } else if (type == boolean.class) {
                return false;
            } else if (type == char.class) {
                return '\u0000';
            }
        }
        return null;
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }

    @Override
    public int getType() {
        return 2; // protobuf序列化方式
    }

    @Override
    public String getSerializerName() {
        return "ProtobufSerializer";
    }
}