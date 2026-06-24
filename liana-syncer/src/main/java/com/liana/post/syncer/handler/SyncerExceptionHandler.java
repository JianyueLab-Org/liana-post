package com.liana.post.syncer.handler;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SyncerExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        LOGGER.warn("syncer business error: {}", exception.getMessage());
        return Result.error(exception.getMessage(), exception.getCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("validation failed");
        return Result.error(message, 400);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        LOGGER.error("syncer internal error", exception);
        return Result.error("syncer internal error", 500);
    }
}
