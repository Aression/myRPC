package client.proxy;

import client.rpcClient.RpcClient;
import client.rpcClient.impl.NettyRpcClient;
import client.rpcClient.impl.SimpleSocketRpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

/*
 * JDK动态代理类，通过工厂模式接收调用的服务对象，通过invoke方法实现代理功能
 * 注意这里实现的InovationHandler，所以是动态代理
 */
@AllArgsConstructor
public class ClientProxy implements InvocationHandler {
    private RpcClient rpcClient;
    public ClientProxy(){
        rpcClient = new NettyRpcClient();
    }
//    public ClientProxy(String host,int port,int choose){
//        switch (choose){
//            case 0:
//                rpcClient = new NettyRpcClient(host, port);
//                break;
//            case 1:
//                rpcClient = new SimpleSocketRpcClient(host,port);
//        }
//    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        //执行request封装，通过宣称的class信息构建一个request并与服务器通信获取回应，返回数据
        RpcRequest request = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args)
                .paramsType(method.getParameterTypes()).build();
        RpcResponse response= rpcClient.sendRequest(request);
        
        // 检查方法返回类型是否为Result
        Type returnType = method.getGenericReturnType();
        boolean isResultType = false;
        Type actualTypeArgument = null;
        
        if (returnType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) returnType;
            Type rawType = parameterizedType.getRawType();
            if (rawType.equals(Result.class)) {
                isResultType = true;
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0) {
                    actualTypeArgument = typeArguments[0];
                }
            }
        }
        
        // 如果方法返回类型是Result，需要构造Result对象
        if (isResultType) {
            if (response.getCode() == 200) {
                // 成功，使用data构造成功的Result
                Object data = response.getData();
                
                // 如果data为null，直接返回空数据的成功结果
                if (data == null) {
                    return Result.success(null, response.getMessage() != null ? response.getMessage() : "操作成功");
                }
                
                // 处理data与actualTypeArgument类型不匹配的情况
                if (actualTypeArgument instanceof Class<?>) {
                    Class<?> typeClass = (Class<?>) actualTypeArgument;
                    if (!typeClass.isInstance(data)) {
                        data = convertDataType(data, typeClass);
                    }
                }
                
                return Result.success(data, response.getMessage() != null ? response.getMessage() : "操作成功");
            } else {
                // 失败，构造失败的Result
                return Result.fail(response.getCode(), response.getMessage());
            }
        }
        
        // 下面是原来的类型转换逻辑，用于兼容老代码
        Object data = response.getData();
        Class<?> methodReturnType = method.getReturnType();
        
        // 如果数据为空或者类型已经匹配，直接返回
        if (data == null || methodReturnType.isInstance(data)) {
            return data;
        }
        
        // 如果数据类型不匹配，尝试转换
        try {
            // 如果是基本类型的返回值
            if (methodReturnType.isPrimitive() || Number.class.isAssignableFrom(methodReturnType) 
                    || Boolean.class == methodReturnType) {
                // 如果数据是字符串，尝试解析
                if (data instanceof String) {
                    String strData = (String) data;
                    if (methodReturnType == int.class || methodReturnType == Integer.class) {
                        return Integer.parseInt(strData);
                    } else if (methodReturnType == boolean.class || methodReturnType == Boolean.class) {
                        return Boolean.parseBoolean(strData);
                    } else if (methodReturnType == long.class || methodReturnType == Long.class) {
                        return Long.parseLong(strData);
                    } else if (methodReturnType == double.class || methodReturnType == Double.class) {
                        return Double.parseDouble(strData);
                    } else if (methodReturnType == float.class || methodReturnType == Float.class) {
                        return Float.parseFloat(strData);
                    }
                }
            } 
            // 如果期望返回复杂对象，但收到字符串（可能是JSON）
            else if (data instanceof String && !methodReturnType.equals(String.class)) {
                String jsonStr = (String) data;
                // 检查是否是JSON格式
                if (jsonStr.trim().startsWith("{") && jsonStr.trim().endsWith("}")) {
                    // 尝试JSON反序列化
                    return com.alibaba.fastjson.JSON.parseObject(jsonStr, methodReturnType);
                }
            }
            
            // 如果无法自动转换，记录错误并抛出异常
            System.out.println("类型不匹配：期望 " + methodReturnType.getName() + 
                              "，实际是 " + data.getClass().getName());
            throw new ClassCastException("无法将 " + data.getClass().getName() + 
                                        " 转换为 " + methodReturnType.getName());
        } catch (Exception e) {
            throw new RuntimeException("数据类型转换失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将数据转换为指定类型
     */
    private Object convertDataType(Object data, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return data.toString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (data instanceof String) {
                    return Integer.parseInt((String) data);
                } else if (data instanceof Number) {
                    return ((Number) data).intValue();
                }
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                if (data instanceof String) {
                    return Boolean.parseBoolean((String) data);
                }
            } else if (targetType == Long.class || targetType == long.class) {
                if (data instanceof String) {
                    return Long.parseLong((String) data);
                } else if (data instanceof Number) {
                    return ((Number) data).longValue();
                }
            } else if (targetType == Double.class || targetType == double.class) {
                if (data instanceof String) {
                    return Double.parseDouble((String) data);
                } else if (data instanceof Number) {
                    return ((Number) data).doubleValue();
                }
            } else if (targetType == Float.class || targetType == float.class) {
                if (data instanceof String) {
                    return Float.parseFloat((String) data);
                } else if (data instanceof Number) {
                    return ((Number) data).floatValue();
                }
            } else if (data instanceof String) {
                // 尝试使用JSON解析
                String jsonStr = (String) data;
                if (jsonStr.trim().startsWith("{") && jsonStr.trim().endsWith("}")) {
                    return com.alibaba.fastjson.JSON.parseObject(jsonStr, targetType);
                }
            }
        } catch (Exception e) {
            System.out.println("类型转换失败: " + e.getMessage());
        }
        return data;
    }

    //动态生成一个指定接口的代理对象
    public <T>T getProxy(Class<T> clazz){
        Object o = Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                this
        );
        return (T)o;
    }
}
