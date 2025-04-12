package common.serializer.impl;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.serializer.Serializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class JsonSerializer implements Serializer{

    @Override
    public byte[] serialize(Object obj) {
        try {
            System.out.println("开始序列化对象: " + obj.getClass().getName());
            byte[] bytes = JSONObject.toJSONBytes(obj);
            System.out.println("序列化完成，数据长度: " + bytes.length);
            return bytes;
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
                    // 将响应解析为request
                    RpcRequest request = JSON.parseObject(bytes, RpcRequest.class);
                    Object[] objects = new Object[request.getParams().length];
                    for(int i=0;i<objects.length;i++){
                        Class<?> paramType = request.getParamsType()[i];
                        if(!paramType.isAssignableFrom(request.getParams()[i].getClass())){
                            // 类型不符合时进行类型转换
                            objects[i] = JSONObject.toJavaObject(
                                (JSONObject) request.getParams()[i], 
                                request.getParamsType()[i]
                            );
                        }else{
                            objects[i] = request.getParams()[i]; // 类型符合时直接赋值
                        }
                    }
                    request.setParams(objects);
                    obj = request;
                    break;
                case 1:
                    // 解析为response
                    RpcResponse response = JSON.parseObject(bytes, RpcResponse.class);
                    Class<?> dataType = response.getDataType();
                    if(!dataType.isAssignableFrom(response.getData().getClass())){
                        response.setData(
                            JSONObject.toJavaObject((JSONObject) response.getData(), dataType)
                        );
                    }
                    obj = response;
                    break;
                default:
                    System.out.println("不支持的消息类型: " + messageType);
                    throw new RuntimeException("不支持的消息类型: " + messageType);
            }
            System.out.println("反序列化完成: " + obj);
            return obj;
        } catch (Exception e) {
            System.out.println("反序列化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("反序列化失败", e);
        }
    }

    @Override
    public int getType() {
        return 1; // json序列化方式
    }

    @Override
    public String getSerializerName() {
        return "JsonSerializer";
    }
    
}
