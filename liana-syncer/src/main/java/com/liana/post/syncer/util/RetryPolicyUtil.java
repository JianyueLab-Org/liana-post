package com.liana.post.syncer.util;

import com.liana.post.common.constant.LogisticsConstants;

/**
 * 重试策略工具。
 */
public final class RetryPolicyUtil {

    private RetryPolicyUtil() {
    }

    public static long nextDelaySeconds(int attemptCount) {
        if (attemptCount <= 0) {
            return LogisticsConstants.INITIAL_RETRY_DELAY;
        }
        return (long) (LogisticsConstants.INITIAL_RETRY_DELAY * Math.pow(LogisticsConstants.RETRY_BACKOFF_BASE, attemptCount - 1));
    }
}