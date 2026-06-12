package com.liana.post.common.model;

/**
 * 通用 API 响应包装器
 * 
 * 支持泛型，用于所有微服务的统一响应格式。
 * 包含：状态码、消息、数据载荷、时间戳。
 * 
 * @param <T> 数据载荷的泛型类型
 */
public class Result<T> {
    
    /**
     * 状态码（HTTP 风格）
     * 200: 成功
     * 400: 客户端错误
     * 500: 服务器错误
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 数据载荷
     */
    private T data;
    
    /**
     * 请求处理时间戳（毫秒）
     */
    private Long timestamp;
    
    // ==================== 构造函数 ====================
    
    public Result() {
    }
    
    public Result(Integer code, String message, T data, Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }
    
    // ==================== Getter & Setter ====================
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 构建成功响应（无数据）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }
    
    /**
     * 构建成功响应（带数据）
     */
    public static <T> Result<T> ok(T data) {
        return ok(data, "操作成功");
    }
    
    /**
     * 构建成功响应（带数据和消息）
     */
    public static <T> Result<T> ok(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
    
    /**
     * 构建错误响应
     */
    public static <T> Result<T> error(String message) {
        return error(message, 500);
    }
    
    /**
     * 构建错误响应（带状态码）
     */
    public static <T> Result<T> error(String message, Integer code) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
    
    /**
     * 构建业务错误响应
     */
    public static <T> Result<T> fail(String message) {
        return error(message, 400);
    }
    
    // ==================== 链式调用方法 ====================
    
    /**
     * 链式调用：设置状态码
     */
    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }
    
    /**
     * 链式调用：设置消息
     */
    public Result<T> message(String message) {
        this.message = message;
        return this;
    }
    
    /**
     * 链式调用：设置数据
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }
    
    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
    
    @Override
    public String toString() {
        return "Result{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", timestamp=" + timestamp +
                '}';
    }
}


