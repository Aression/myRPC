package server.server.work;

import common.message.RpcRequest;
import common.message.RpcResponse;
import common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.provider.ServiceProvider;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

public class WorkThread implements java.lang.Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WorkThread.class);
    private Socket socket;//网络连接
    private ServiceProvider serviceProvider;//服务注册中心

    public WorkThread(Socket socket, ServiceProvider serviceProvider) {
        this.socket = socket;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void run() {
        try {
            // 实例化网络输入输出流
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            // 先读取request，然后通过反射获取返回值
            RpcRequest rpcRequest = (RpcRequest) ois.readObject();
            RpcResponse rpcResponse = getResponse(rpcRequest);

            // 向客户端写入response
            oos.writeObject(rpcResponse);
            oos.flush();
        } catch (IOException|ClassNotFoundException e) {
            logger.error("服务端处理请求时出错: {}", e.getMessage(), e);
        }
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {
        try {
            // 获取服务名
            String interfaceName = rpcRequest.getInterfaceName();
            logger.info("处理服务请求: {}", interfaceName);
            // 得到服务端相应实现类
            Object service = serviceProvider.getService(interfaceName);
            Method method;
            try {
                // 获取方法对象
                method = service.getClass().getMethod(
                        rpcRequest.getMethodName(), rpcRequest.getParamsType()
                );
                logger.info("调用方法: {}", rpcRequest.getMethodName());
                //通过反射调用方法
                Object invoke = method.invoke(service, rpcRequest.getParams());
                logger.info("方法调用成功，返回结果: {}", invoke);

                // 如果返回值是Result类型，根据Result状态转换为RpcResponse
                if (invoke instanceof Result) {
                    Result<?> result = (Result<?>) invoke;
                    if (result.isSuccess()) {
                        return RpcResponse.success(result.getData());
                    } else {
                        return RpcResponse.fail(result.getCode(), result.getMessage());
                    }
                } else {
                    return RpcResponse.success(invoke);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                logger.error("服务端执行方法时出错: {}", e.getMessage(), e);
                return RpcResponse.fail(500, "服务端执行方法时出错");
            }
        } catch (Exception e) {
            logger.error("服务端处理请求时出错: {}", e.getMessage(), e);
            return RpcResponse.fail(500, "服务端处理请求时出错");
        }
    }
}
