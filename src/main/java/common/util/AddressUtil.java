package common.util;

import java.net.InetSocketAddress;

public class AddressUtil {
    private AddressUtil() {} // 私有构造函数，防止实例化
    
    public static String toString(InetSocketAddress address) {
        return address.getHostName() + ":" + address.getPort();
    }
    
    public static InetSocketAddress fromString(String address) {
        String[] parts = address.split(":");
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }
}