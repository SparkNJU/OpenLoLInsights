package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean ok;
    private T data;
    private ApiError error;
    private String traceId;

    public ApiResponse() {}

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        ApiResponse<T> r = new ApiResponse<>();
        r.ok = true;
        r.data = data;
        r.traceId = traceId;
        return r;
    }

    public static <T> ApiResponse<T> fail(ApiError error, String traceId) {
        ApiResponse<T> r = new ApiResponse<>();
        r.ok = false;
        r.error = error;
        r.traceId = traceId;
        return r;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public ApiError getError() { return error; }
    public void setError(ApiError error) { this.error = error; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}

