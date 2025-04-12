package common.message;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MessageType{
    // 规定消息类型对应码
    REQUEST(0),RESPONSE(1);

    // 返回对应码
    private int code;
    public int getCode(){
        return code;
    }

    // 校验消息类型码是否合法
    public static boolean isValid(int code) {
        for (MessageType type : MessageType.values()) {
            if (type.getCode() == code) {
                return true;
            }
        }
        return false;
    }
}
