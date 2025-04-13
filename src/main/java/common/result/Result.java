package common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用返回结果封装类
 * @param <T> 数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 返回码
     */
    private int code;
    
    /**
     * 返回消息
     */
    private String message;
    
    /**
     * 返回数据
     */
    private T data;
    
    /**
     * 成功返回结果
     * @param data 返回数据
     * @param <T> 数据类型
     * @return 成功的结果对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(true, 200, "操作成功", data);
    }
    
    /**
     * 成功返回结果
     * @param data 返回数据
     * @param message 成功消息
     * @param <T> 数据类型
     * @return 成功的结果对象
     */
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(true, 200, message, data);
    }
    
    /**
     * 失败返回结果
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败的结果对象
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(false, 500, message, null);
    }
    
    /**
     * 失败返回结果
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败的结果对象
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(false, code, message, null);
    }
    
    /**
     * 失败返回结果
     * @param code 错误码
     * @param message 错误消息
     * @param data 错误数据
     * @param <T> 数据类型
     * @return 失败的结果对象
     */
    public static <T> Result<T> fail(int code, String message, T data) {
        return new Result<>(false, code, message, data);
    }
} 