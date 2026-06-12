package com.liana.post.syncer.task;

import com.liana.post.syncer.service.SyncerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 重试调度任务。
 */
@Component
public class RetryTask {

    private final SyncerService syncerService;

    public RetryTask(SyncerService syncerService) {
        this.syncerService = syncerService;
    }

    @Scheduled(fixedDelayString = "${syncer.retry.scan-delay-ms:10000}")
    public void retryPendingTasks() {
        syncerService.retryPendingTasks();
    }
}