package client;

import common.message.RpcRequest;
import common.message.RpcResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class IOClient {
    public static RpcResponse sendRequest(String host, int port, RpcRequest request){
        try{
            Socket socket = new Socket(host, port);
            ObjectOutputStream oos = new ObjectOutputStream(
                    socket.getOutputStream()
            );
            ObjectInputStream ois = new ObjectInputStream(
                    socket.getInputStream()
            );

            //序列化RpcRequest对象，通过输出流发送到服务端
            oos.writeObject(request);
            oos.flush(); //刷新输出流，保证数据立即被发送

            //从输入流中读取服务端返回的序列化对象，反序列化为对应的RpcResponse
            return (RpcResponse) ois.readObject();
        }catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }
    }
}
