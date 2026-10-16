package com.mars.common.web;

import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常 → 统一响应（契约 §2.2 / §2.7）。
 *
 * <p>关键：<b>{@code code} 与 HTTP 状态码并出</b>，且 HTTP 状态严格取自
 * {@link ErrorCode#httpStatus()}（如 {@code 40300/40325 → 403}、{@code 40330 → 404}、
 * {@code 42900 → 429}），前端以 {@code code} 分支。
 */
@RestControllerAdvice
public class ApiResponseAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseAdvice.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
        ErrorCode code = ex.errorCode() == null ? ErrorCode.SYSTEM_ERROR : ex.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.fail(code, ex.getMessage(), TraceContext.get()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, detail, TraceContext.get()));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, ex.getMessage(), TraceContext.get()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.httpStatus())
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR, null, TraceContext.get()));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
