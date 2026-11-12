package com.liana.post.syncer.controller;

import com.liana.post.common.model.Result;
import com.liana.post.syncer.model.dto.SyncPlanDto;
import com.liana.post.syncer.model.entity.OutboxMessageEntity;
import com.liana.post.syncer.model.entity.RetryRecordEntity;
import com.liana.post.syncer.model.entity.SyncTaskEntity;
import com.liana.post.syncer.service.SyncerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/syncer")
public class SyncerController {

    private final SyncerService syncerService;

    public SyncerController(SyncerService syncerService) {
        this.syncerService = syncerService;
    }

    @PostMapping("/plans")
    public Result<List<OutboxMessageEntity>> submitPlan(@Valid @RequestBody SyncPlanDto request) {
        return Result.ok(syncerService.submitPlan(request));
    }

    @PostMapping("/demo/success")
    public Result<List<OutboxMessageEntity>> createSuccessDemo() {
        SyncPlanDto request = new SyncPlanDto();
        request.setSourceService("liana-oms-service");
        request.setTargetService("liana-tracking-service");
        request.setBizType("MAIL_STATUS_CHANGED");
        request.setBizIds(List.of("DEMO-SUCCESS-" + System.currentTimeMillis()));
        request.setAttributes(Map.of("status", "DISPATCHED", "operator", "syncer-demo"));
        return Result.ok(syncerService.submitPlan(request));
    }

    @PostMapping("/demo/fail-once")
    public Result<List<OutboxMessageEntity>> createFailOnceDemo() {
        SyncPlanDto request = new SyncPlanDto();
        request.setSourceService("liana-dispatch-service");
        request.setTargetService("liana-tracking-service");
        request.setBizType("DISPATCH_BAG_SEALED");
        request.setBizIds(List.of("DEMO-RETRY-" + System.currentTimeMillis()));
        request.setFailFirstAttempt(true);
        request.setAttributes(Map.of("bagNo", "BAG-DEMO", "operator", "syncer-demo"));
        return Result.ok(syncerService.submitPlan(request));
    }

    @PostMapping("/outbox/scan")
    public Result<Integer> scanOutboxOnce() {
        return Result.ok(syncerService.scanOutboxOnce());
    }

    @PostMapping("/tasks/retry")
    public Result<Integer> retryPendingTasks() {
        return Result.ok(syncerService.retryPendingTasks());
    }

    @GetMapping("/outbox")
    public Result<List<OutboxMessageEntity>> listOutboxMessages() {
        return Result.ok(syncerService.listOutboxMessages());
    }

    @GetMapping("/tasks")
    public Result<List<SyncTaskEntity>> listSyncTasks() {
        return Result.ok(syncerService.listSyncTasks());
    }

    @GetMapping("/retries")
    public Result<List<RetryRecordEntity>> listRetryRecords() {
        return Result.ok(syncerService.listRetryRecords());
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(syncerService.dashboard());
    }
}
