package com.liana.post.syncer.service;

import com.liana.post.syncer.model.dto.SyncPlanDto;
import com.liana.post.syncer.model.entity.OutboxMessageEntity;
import com.liana.post.syncer.model.entity.RetryRecordEntity;
import com.liana.post.syncer.model.entity.SyncTaskEntity;
import java.util.List;
import java.util.Map;

/**
 * 同步服务抽象。
 */
public interface SyncerService {

    List<OutboxMessageEntity> submitPlan(SyncPlanDto syncPlanDto);

    int scanOutboxOnce();

    int retryPendingTasks();

    List<OutboxMessageEntity> listOutboxMessages();

    List<SyncTaskEntity> listSyncTasks();

    List<RetryRecordEntity> listRetryRecords();

    Map<String, Object> dashboard();
}
