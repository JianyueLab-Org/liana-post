package com.liana.post.syncer.task;

import com.liana.post.syncer.service.SyncerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox 扫描任务。
 */
@Component
public class OutboxScanTask {

    private final SyncerService syncerService;

    public OutboxScanTask(SyncerService syncerService) {
        this.syncerService = syncerService;
    }

    @Scheduled(fixedDelayString = "${syncer.outbox.scan-delay-ms:5000}")
    public void scanOnce() {
        syncerService.scanOutboxOnce();
    }
}