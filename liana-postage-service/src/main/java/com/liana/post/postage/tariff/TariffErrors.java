package com.liana.post.postage.tariff;

import com.liana.post.common.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;

@RestControllerAdvice
public class TariffErrors {
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> duplicate(DuplicateKeyException e) {
        return ResponseEntity.status(409).body(Result.error("请求编号或凭证发生冲突，请查询业务结果后重试", 409));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Result.error(e.getReason(), e.getStatusCode().value()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> invalid(Exception e) {
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "请求字段或 JSON 格式无效";
        return ResponseEntity.badRequest().body(Result.error(message, 400));
    }
}
