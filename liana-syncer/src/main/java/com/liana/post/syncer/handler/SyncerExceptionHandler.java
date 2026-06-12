package com.liana.post.syncer.handler;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Syncer 异常处理占位器。
 * TODO: 若后续增加 HTTP 管理接口，再改为 @RestControllerAdvice。
 */
public class SyncerExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerExceptionHandler.class);

    public Result<Void> handleBusinessException(BusinessException exception) {
        LOGGER.warn("业务异常: {}", exception.getMessage());
        return Result.error(exception.getMessage(), exception.getCode());
    }

    public Result<Void> handleException(Exception exception) {
        LOGGER.error("系统异常", exception);
        return Result.error("syncer internal error", 500);
    }
}