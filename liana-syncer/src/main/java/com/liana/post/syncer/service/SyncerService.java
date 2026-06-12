package com.liana.post.syncer.service;

import com.liana.post.syncer.model.dto.SyncPlanDto;

/**
 * 同步服务抽象。
 */
public interface SyncerService {

    void submitPlan(SyncPlanDto syncPlanDto);

    void scanOutboxOnce();

    void retryPendingTasks();
}