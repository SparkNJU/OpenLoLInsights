package com.example.backend.exception;

import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiError;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        HttpStatus status;
        String code = e.getCode();

        if ("INVALID_ARGUMENT".equals(code) || "AI_INVALID_ARGUMENT".equals(code)) {
            status = HttpStatus.BAD_REQUEST;
        } else if ("UNAUTHORIZED".equals(code)) {
            status = HttpStatus.UNAUTHORIZED;
        } else if ("FORBIDDEN".equals(code)) {
            status = HttpStatus.FORBIDDEN;
        } else if ("NOT_FOUND".equals(code)) {
            status = HttpStatus.NOT_FOUND;
        } else if ("CONFLICT".equals(code)) {
            status = HttpStatus.CONFLICT;
        } else if (code != null && code.startsWith("AI_")) {
            // 上游（agent/LLM）错误默认视为 502，避免前端误以为是业务 400
            status = HttpStatus.BAD_GATEWAY;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        ApiError err = new ApiError(e.getCode(), e.getMessage(), e.getDetails());
        return ResponseEntity.status(status).body(ApiResponse.fail(err, TraceIdUtil.getOrCreate()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        Map<String, String> details = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        ApiError err = new ApiError("INVALID_ARGUMENT", "参数校验失败", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(err, TraceIdUtil.getOrCreate()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCv(ConstraintViolationException e) {
        ApiError err = new ApiError("INVALID_ARGUMENT", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(err, TraceIdUtil.getOrCreate()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        // 至少打印堆栈，避免日志里只剩 Tomcat /error
        e.printStackTrace();
        ApiError err = new ApiError("INTERNAL_ERROR", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(err, TraceIdUtil.getOrCreate()));
    }
}
