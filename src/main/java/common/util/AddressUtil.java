package common.util;

import java.net.InetSocketAddress;

public class AddressUtil {
    private AddressUtil() {} // 私有构造函数，防止实例化
    
    public static String toString(InetSocketAddress address) {
        // TODO：使用getHostString()方法获取主机名，而不是getHostName()，避免本地环境下发生反向 DNS 解析阻塞
        // 因为私有 IP 的反向解析记录通常不存在于公网 DNS 中，而本地网络又缺乏相应的解析服务支持，所以会长时间阻塞
        return address.getHostString() + ":" + address.getPort();
    }
    
    public static InetSocketAddress fromString(String address) {
        String[] parts = address.split(":");
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }
}