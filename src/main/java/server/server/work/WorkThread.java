package server.server.work;

import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.AllArgsConstructor;
import server.provider.ServiceProvider;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

@AllArgsConstructor
public class WorkThread implements Runnable{
    private Socket socket;//网络连接
    private ServiceProvider serviceProvider;//服务注册中心

    @Override
    public void run() {
        try{
            // 实例化网络输入输出流
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            // 先读取request，然后通过反射获取返回值
            RpcRequest rpcRequest = (RpcRequest) ois.readObject();
            RpcResponse rpcResponse =  getResponse(rpcRequest);

            // 向客户端写入response
            oos.writeObject(rpcResponse);
            oos.flush();
        } catch (IOException|ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private RpcResponse getResponse(RpcRequest rpcRequest){
        // 获取服务名
        String interfaceName = rpcRequest.getInterfaceName();
        // 得到服务端相应实现类
        Object service = serviceProvider.getService(interfaceName);
        Method method = null;
        try{
            // 获取方法对象
            method = service.getClass().getMethod(
                    rpcRequest.getMethodName(), rpcRequest.getParamsType()
            );
            //通过反射调用方法
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.success(invoke);
        }catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 找不到请求的方法or方法无法访问or方法执行过程中出错
            e.printStackTrace();
            System.out.println("服务端执行方法时出错");
            return RpcResponse.fail();
        }
    }
}
