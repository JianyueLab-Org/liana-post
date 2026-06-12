package com.liana.post.facility.handler;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FacilityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacilityExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) { LOGGER.warn("设施业务异常: {}", exception.getMessage()); return Result.error(exception.getMessage(), exception.getCode()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) { String message = exception.getBindingResult().getAllErrors().isEmpty() ? "validation failed" : exception.getBindingResult().getAllErrors().get(0).getDefaultMessage(); return Result.error(message, 400); }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) { LOGGER.error("设施模块系统异常", exception); return Result.error("facility internal error", 500); }
}