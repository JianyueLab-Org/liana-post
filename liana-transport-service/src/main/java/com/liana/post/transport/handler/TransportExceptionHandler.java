package com.liana.post.transport.handler;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TransportExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(TransportExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException exception) {
        return Result.error(exception.getMessage(), exception.getCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("validation error");
        return Result.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception exception) {
        log.error("transport internal error", exception);
        return Result.error("transport internal error", 500);
    }
}
