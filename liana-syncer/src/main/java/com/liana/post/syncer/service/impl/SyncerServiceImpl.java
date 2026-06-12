package com.liana.post.syncer.service.impl;

import com.liana.post.syncer.model.dto.SyncPlanDto;
import com.liana.post.syncer.service.SyncerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 同步服务实现。
 * TODO: 对接消息总线、数据库轮询、重试调度与幂等落库。
 */
@Service
public class SyncerServiceImpl implements SyncerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerServiceImpl.class);

    @Override
    public void submitPlan(SyncPlanDto syncPlanDto) {
        LOGGER.info("提交同步计划: {}", syncPlanDto);
        // TODO: 根据业务服务写入 outbox 或 sync_task
    }

    @Override
    public void scanOutboxOnce() {
        // TODO: 扫描 outbox_message，转换为同步任务并投递
    }

    @Override
    public void retryPendingTasks() {
        // TODO: 执行失败任务重试、指数退避和失败收敛
    }
}