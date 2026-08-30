package com.smartcare.icustats.config;

import com.smartcare.icustats.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        String message = e.getMessage();
        if (message == null) message = "服务器异常";
        int status = message.contains("格式") || message.contains("缺失") || message.contains("不能")
                || message.contains("超过") || message.contains("不支持") ? 400 : 500;
        if (status == 500) log.error("Server error", e);
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "参数缺失: " + e.getParameterName();
        return ResponseEntity.status(400).body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e, HttpServletResponse response) {
        log.error("Server error", e);

        if (response.isCommitted()) {
            return null;
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
