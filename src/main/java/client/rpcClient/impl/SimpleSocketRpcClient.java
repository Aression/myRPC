package client.rpcClient.impl;

import client.rpcClient.RpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

@AllArgsConstructor
public class SimpleSocketRpcClient implements RpcClient {
    private String host;
    private int port;

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        try{
            Socket socket = new Socket(host, port);
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            oos.writeObject(request);
            oos.flush();

            return (RpcResponse) ois.readObject();
        } catch (IOException|ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

	@Override
	public boolean checkRetry(String serviceName) {
		return false; // 默认不可重试
	}
}
