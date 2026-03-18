package com.example.myapplication.model;

/**
 * 通用 API 响应体
 *
 * 对应服务端 com.sip.common.result.Result：
 * { "code": 200, "message": "...", "data": { ... } }
 */
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public boolean isSuccess() { return code == 200; }
}
