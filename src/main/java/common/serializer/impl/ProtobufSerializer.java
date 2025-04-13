package common.serializer.impl;

import common.message.Rpc;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.ArrayList;
import java.util.List;

public class ProtobufSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        try {
            System.out.println("开始序列化对象: " + obj.getClass().getName());
            
            // 处理RpcRequest
            if (obj instanceof RpcRequest) {
                RpcRequest javaRequest = (RpcRequest) obj;
                
                // 构建Protobuf版本的RpcRequest
                Rpc.RpcRequest.Builder builder = Rpc.RpcRequest.newBuilder();
                
                // 设置interfaceName
                if (javaRequest.getInterfaceName() != null) {
                    builder.setInterfaceName(javaRequest.getInterfaceName());
                }
                
                // 设置methodName
                if (javaRequest.getMethodName() != null) {
                    builder.setMethodName(javaRequest.getMethodName());
                }
                
                // 处理params
                if (javaRequest.getParams() != null) {
                    for (Object param : javaRequest.getParams()) {
                        if (param != null) {
                            // 对于复杂对象，使用JSON序列化
                            if (!(param instanceof String || param instanceof Number || param instanceof Boolean)) {
                                String jsonParam = com.alibaba.fastjson.JSON.toJSONString(param);
                                builder.addParams(jsonParam);
                            } else {
                                builder.addParams(param.toString());
                            }
                        } else {
                            builder.addParams("");
                        }
                    }
                }
                
                // 处理paramsType
                if (javaRequest.getParamsType() != null) {
                    for (Class<?> paramType : javaRequest.getParamsType()) {
                        if (paramType != null) {
                            builder.addParamsType(paramType.getName());
                        } else {
                            builder.addParamsType("");
                        }
                    }
                }
                
                Rpc.RpcRequest protoRequest = builder.build();
                byte[] bytes = protoRequest.toByteArray();
                System.out.println("序列化完成，数据长度: " + bytes.length);
                return bytes;
            } 
            // 处理RpcResponse
            else if (obj instanceof RpcResponse) {
                RpcResponse javaResponse = (RpcResponse) obj;
                
                Rpc.RpcResponse.Builder builder = Rpc.RpcResponse.newBuilder();
                
                // 设置data
                if (javaResponse.getData() != null) {
                    // 对于复杂对象，使用JSON序列化
                    if (!(javaResponse.getData() instanceof String || 
                          javaResponse.getData() instanceof Number || 
                          javaResponse.getData() instanceof Boolean)) {
                        String jsonData = com.alibaba.fastjson.JSON.toJSONString(javaResponse.getData());
                        builder.setData(jsonData);
                    } else {
                        builder.setData(javaResponse.getData().toString());
                    }
                } else {
                    builder.setData("");
                }
                
                // 设置dataType
                if (javaResponse.getDataType() != null) {
                    builder.setDataType(javaResponse.getDataType().getName());
                } else {
                    builder.setDataType("");
                }
                
                // 设置code
                builder.setCode(javaResponse.getCode());
                
                // 设置message
                if (javaResponse.getMessage() != null) {
                    builder.setMessage(javaResponse.getMessage());
                } else {
                    builder.setMessage("");
                }
                
                Rpc.RpcResponse protoResponse = builder.build();
                byte[] bytes = protoResponse.toByteArray();
                System.out.println("序列化完成，数据长度: " + bytes.length);
                return bytes;
            }
            // 处理直接的Protocol Buffers消息
            else if (obj instanceof MessageLite) {
                byte[] bytes = ((MessageLite) obj).toByteArray();
                System.out.println("序列化完成，数据长度: " + bytes.length);
                return bytes;
            } 
            else {
                throw new IllegalArgumentException("不支持的对象类型: " + obj.getClass().getName());
            }
        } catch (Exception e) {
            System.out.println("序列化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("序列化失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        try {
            System.out.println("开始反序列化，消息类型: " + messageType);
            Object obj = null;
            switch (messageType) {
                case 0:
                    // 解析为RpcRequest
                    Rpc.RpcRequest protoRequest = Rpc.RpcRequest.parseFrom(bytes);
                    
                    // 转换为Java Bean的RpcRequest
                    RpcRequest javaRequest = new RpcRequest();
                    
                    // 设置interfaceName
                    javaRequest.setInterfaceName(protoRequest.getInterfaceName());
                    
                    // 设置methodName
                    javaRequest.setMethodName(protoRequest.getMethodName());
                    
                    // 转换params
                    List<String> paramsList = protoRequest.getParamsList();
                    Object[] params = new Object[paramsList.size()];
                    
                    // 获取参数类型列表
                    List<String> paramsTypeList = protoRequest.getParamsTypeList();
                    Class<?>[] paramsTypes = new Class<?>[paramsTypeList.size()];
                    
                    // 先初始化参数类型数组
                    for (int i = 0; i < paramsTypeList.size(); i++) {
                        try {
                            paramsTypes[i] = Class.forName(paramsTypeList.get(i));
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                            paramsTypes[i] = String.class; // 默认使用String类
                        }
                    }
                    
                    // 根据参数类型转换参数值
                    for (int i = 0; i < paramsList.size(); i++) {
                        String paramStr = paramsList.get(i);
                        Class<?> paramType = paramsTypes[i];
                        
                        try {
                            // 根据目标类型进行转换
                            if (paramType == String.class) {
                                params[i] = paramStr;
                            } else if (paramType == Integer.class || paramType == int.class) {
                                params[i] = Integer.parseInt(paramStr);
                            } else if (paramType == Boolean.class || paramType == boolean.class) {
                                params[i] = Boolean.parseBoolean(paramStr);
                            } else if (paramType == Double.class || paramType == double.class) {
                                params[i] = Double.parseDouble(paramStr);
                            } else if (paramType == Float.class || paramType == float.class) {
                                params[i] = Float.parseFloat(paramStr);
                            } else if (paramType == Long.class || paramType == long.class) {
                                params[i] = Long.parseLong(paramStr);
                            } else {
                                // 对于复杂对象，使用JSON反序列化
                                try {
                                    params[i] = com.alibaba.fastjson.JSON.parseObject(paramStr, paramType);
                                    System.out.println("成功反序列化复杂对象: " + params[i]);
                                } catch (Exception jsonEx) {
                                    System.out.println("JSON反序列化失败，尝试使用默认构造函数: " + jsonEx.getMessage());
                                    // 如果JSON反序列化失败，尝试使用反射创建对象并设置属性
                                    Object instance = paramType.getDeclaredConstructor().newInstance();
                                    params[i] = instance;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("参数转换失败: " + e.getMessage());
                            e.printStackTrace();
                            // 转换失败不要直接使用原字符串，而是返回null或默认值
                            try {
                                if (paramType.isPrimitive()) {
                                    // 对于原始类型，使用默认值
                                    if (paramType == int.class || paramType == long.class || 
                                        paramType == short.class || paramType == byte.class) {
                                        params[i] = 0;
                                    } else if (paramType == float.class || paramType == double.class) {
                                        params[i] = 0.0;
                                    } else if (paramType == boolean.class) {
                                        params[i] = false;
                                    } else if (paramType == char.class) {
                                        params[i] = '\u0000';
                                    }
                                } else {
                                    params[i] = null;
                                }
                            } catch (Exception ex) {
                                params[i] = null;
                            }
                        }
                    }
                    
                    javaRequest.setParams(params);
                    
                    // 转换paramsType
                    javaRequest.setParamsType(paramsTypes);
                    
                    obj = javaRequest;
                    break;
                    
                case 1:
                    // 解析为RpcResponse
                    Rpc.RpcResponse protoResponse = Rpc.RpcResponse.parseFrom(bytes);
                    
                    // 转换为Java Bean的RpcResponse
                    RpcResponse javaResponse = new RpcResponse();
                    
                    // 设置code
                    javaResponse.setCode(protoResponse.getCode());
                    
                    // 设置message
                    javaResponse.setMessage(protoResponse.getMessage());
                    
                    // 设置data和dataType
                    String dataTypeStr = protoResponse.getDataType();
                    if (dataTypeStr != null && !dataTypeStr.isEmpty()) {
                        try {
                            Class<?> dataType = Class.forName(dataTypeStr);
                            javaResponse.setDataType(dataType);
                            
                            String dataStr = protoResponse.getData();
                            System.out.println("数据类型: " + dataType.getName() + ", 数据内容: " + dataStr);
                            
                            // 根据数据类型进行转换
                            if (dataType == String.class) {
                                javaResponse.setData(dataStr);
                            } else if (dataType == Integer.class || dataType == int.class) {
                                javaResponse.setData(Integer.parseInt(dataStr));
                            } else if (dataType == Boolean.class || dataType == boolean.class) {
                                javaResponse.setData(Boolean.parseBoolean(dataStr));
                            } else if (dataType == Double.class || dataType == double.class) {
                                javaResponse.setData(Double.parseDouble(dataStr));
                            } else if (dataType == Float.class || dataType == float.class) {
                                javaResponse.setData(Float.parseFloat(dataStr));
                            } else if (dataType == Long.class || dataType == long.class) {
                                javaResponse.setData(Long.parseLong(dataStr));
                            } else {
                                try {
                                    // 检查是否是有效的JSON字符串
                                    if (dataStr.trim().startsWith("{") && dataStr.trim().endsWith("}")) {
                                        // 复杂对象使用JSON反序列化
                                        Object resultObj = com.alibaba.fastjson.JSON.parseObject(dataStr, dataType);
                                        javaResponse.setData(resultObj);
                                        System.out.println("成功使用JSON反序列化复杂对象: " + resultObj);
                                    } else {
                                        throw new Exception("数据不是有效的JSON格式: " + dataStr);
                                    }
                                } catch (Exception jsonEx) {
                                    System.out.println("JSON反序列化失败: " + jsonEx.getMessage());
                                    // 尝试使用反射创建对象
                                    try {
                                        Object instance = dataType.getDeclaredConstructor().newInstance();
                                        javaResponse.setData(instance);
                                        System.out.println("使用默认构造函数创建对象: " + instance);
                                    } catch (Exception reflectEx) {
                                        System.out.println("无法创建对象实例: " + reflectEx.getMessage());
                                        javaResponse.setData(dataStr);
                                    }
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            System.out.println("找不到类: " + dataTypeStr);
                            e.printStackTrace();
                            javaResponse.setDataType(String.class);
                            javaResponse.setData(protoResponse.getData());
                        } catch (Exception e) {
                            System.out.println("数据类型转换失败: " + e.getMessage());
                            e.printStackTrace();
                            javaResponse.setData(protoResponse.getData()); // 转换失败时保留原始字符串
                        }
                    } else {
                        javaResponse.setData(protoResponse.getData());
                    }
                    
                    obj = javaResponse;
                    break;
                    
                default:
                    System.out.println("不支持的消息类型: " + messageType);
                    throw new RuntimeException("不支持的消息类型: " + messageType);
            }
            System.out.println("反序列化完成: " + obj);
            return obj;
        } catch (InvalidProtocolBufferException e) {
            System.out.println("反序列化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("反序列化失败", e);
        }
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