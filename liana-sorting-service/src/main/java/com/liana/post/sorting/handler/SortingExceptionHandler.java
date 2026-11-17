package com.liana.post.sorting.handler;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Preserve lifecycle rejection codes for callers and durable event publishers. */
@RestControllerAdvice
public class SortingExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException exception) {
        int code = exception.getCode();
        return ResponseEntity.status(code).body(Result.error(exception.getMessage(), code));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> validation(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error("invalid sorting request", 400));
    }
}
