package com.liana.post.syncer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.common.util.JsonUtil;
import com.liana.post.syncer.constant.SyncerConstants;
import com.liana.post.syncer.model.dto.SyncPlanDto;
import com.liana.post.syncer.model.entity.OutboxMessageEntity;
import com.liana.post.syncer.model.entity.RetryRecordEntity;
import com.liana.post.syncer.model.entity.SyncTaskEntity;
import com.liana.post.syncer.repository.OutboxMessageMapper;
import com.liana.post.syncer.repository.RetryRecordMapper;
import com.liana.post.syncer.repository.SyncTaskMapper;
import com.liana.post.syncer.service.SyncerService;
import com.liana.post.syncer.util.RetryPolicyUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class SyncerServiceImpl implements SyncerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncerServiceImpl.class);
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 20;

    private final OutboxMessageMapper outboxMessageMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final RetryRecordMapper retryRecordMapper;
    private final boolean dispatchOnCreate;

    public SyncerServiceImpl(
            OutboxMessageMapper outboxMessageMapper,
            SyncTaskMapper syncTaskMapper,
            RetryRecordMapper retryRecordMapper,
            @Value("${syncer.demo.dispatch-on-create:true}") boolean dispatchOnCreate) {
        this.outboxMessageMapper = outboxMessageMapper;
        this.syncTaskMapper = syncTaskMapper;
        this.retryRecordMapper = retryRecordMapper;
        this.dispatchOnCreate = dispatchOnCreate;
    }

    @Override
    @Transactional
    public List<OutboxMessageEntity> submitPlan(SyncPlanDto syncPlanDto) {
        validatePlan(syncPlanDto);
        List<OutboxMessageEntity> created = new ArrayList<>();
        for (String bizId : syncPlanDto.getBizIds()) {
            if (!StringUtils.hasText(bizId)) {
                continue;
            }
            OutboxMessageEntity message = new OutboxMessageEntity();
            message.setMsgId(IdGeneratorUtil.generateBizNo("OBX"));
            message.setEventType(syncPlanDto.getBizType().trim());
            message.setPayload(JsonUtil.toJson(buildPayload(syncPlanDto, bizId.trim())));
            message.setStatus(SyncerConstants.STATUS_NEW);
            message.setRetryCount(0);
            message.setMaxRetries(DEFAULT_MAX_RETRIES);
            message.setVersion(0);
            outboxMessageMapper.insert(message);
            created.add(message);
        }
        if (created.isEmpty()) {
            throw new BusinessException("bizIds must contain at least one nonblank value");
        }
        LOGGER.info("created {} outbox messages for sync plan {}", created.size(), syncPlanDto.getBizType());
        if (dispatchOnCreate) {
            scanOutboxOnce();
            retryPendingTasks();
            return created.stream()
                    .map(message -> outboxMessageMapper.selectById(message.getId()))
                    .toList();
        }
        return created;
    }

    @Override
    @Transactional
    public int scanOutboxOnce() {
        List<OutboxMessageEntity> messages = outboxMessageMapper.selectList(outboxScanQuery());
        int processed = 0;
        for (OutboxMessageEntity message : messages) {
            try {
                markOutboxProcessing(message);
                SyncTaskEntity task = buildTask(message);
                syncTaskMapper.insert(task);
                markOutboxSuccess(message);
                processed++;
            } catch (Exception exception) {
                markOutboxFailure(message, exception);
            }
        }
        if (processed > 0) {
            LOGGER.info("converted {} outbox messages to sync tasks", processed);
        }
        return processed;
    }

    @Override
    @Transactional
    public int retryPendingTasks() {
        List<SyncTaskEntity> tasks = syncTaskMapper.selectList(taskRetryQuery());
        int processed = 0;
        for (SyncTaskEntity task : tasks) {
            executeTask(task);
            processed++;
        }
        if (processed > 0) {
            LOGGER.info("processed {} sync tasks", processed);
        }
        return processed;
    }

    @Override
    public List<OutboxMessageEntity> listOutboxMessages() {
        return outboxMessageMapper.selectList(new QueryWrapper<OutboxMessageEntity>()
                .orderByDesc("id")
                .last("LIMIT 50"));
    }

    @Override
    public List<SyncTaskEntity> listSyncTasks() {
        return syncTaskMapper.selectList(new QueryWrapper<SyncTaskEntity>()
                .orderByDesc("id")
                .last("LIMIT 50"));
    }

    @Override
    public List<RetryRecordEntity> listRetryRecords() {
        return retryRecordMapper.selectList(new QueryWrapper<RetryRecordEntity>()
                .orderByDesc("id")
                .last("LIMIT 50"));
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("outbox", statusCounts("outbox_message"));
        dashboard.put("syncTasks", statusCounts("sync_task"));
        dashboard.put("retryRecords", statusCounts("retry_record"));
        dashboard.put("recentOutbox", listOutboxMessages().stream().limit(5).toList());
        dashboard.put("recentTasks", listSyncTasks().stream().limit(5).toList());
        dashboard.put("recentRetries", listRetryRecords().stream().limit(5).toList());
        return dashboard;
    }

    private void validatePlan(SyncPlanDto syncPlanDto) {
        if (syncPlanDto == null) {
            throw new BusinessException("sync plan is required");
        }
        if (!StringUtils.hasText(syncPlanDto.getSourceService())) {
            throw new BusinessException("sourceService is required");
        }
        if (!StringUtils.hasText(syncPlanDto.getTargetService())) {
            throw new BusinessException("targetService is required");
        }
        if (!StringUtils.hasText(syncPlanDto.getBizType())) {
            throw new BusinessException("bizType is required");
        }
        if (CollectionUtils.isEmpty(syncPlanDto.getBizIds())) {
            throw new BusinessException("bizIds is required");
        }
    }

    private Map<String, Object> buildPayload(SyncPlanDto plan, String bizId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceService", plan.getSourceService().trim());
        payload.put("targetService", plan.getTargetService().trim());
        payload.put("bizType", plan.getBizType().trim());
        payload.put("bizId", bizId);
        payload.put("failFirstAttempt", Boolean.TRUE.equals(plan.getFailFirstAttempt()));
        payload.put("alwaysFail", Boolean.TRUE.equals(plan.getAlwaysFail()));
        payload.put("attributes", plan.getAttributes());
        payload.put("demo", true);
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }

    private QueryWrapper<OutboxMessageEntity> outboxScanQuery() {
        LocalDateTime now = LocalDateTime.now();
        return new QueryWrapper<OutboxMessageEntity>()
                .in("status", List.of(SyncerConstants.STATUS_NEW, SyncerConstants.STATUS_FAILED))
                .and(wrapper -> wrapper.isNull("next_retry_time").or().le("next_retry_time", now))
                .apply("retry_count < max_retries")
                .orderByAsc("id")
                .last("LIMIT " + BATCH_SIZE);
    }

    private QueryWrapper<SyncTaskEntity> taskRetryQuery() {
        LocalDateTime now = LocalDateTime.now();
        return new QueryWrapper<SyncTaskEntity>()
                .in("status", List.of(SyncerConstants.STATUS_PENDING, SyncerConstants.STATUS_FAILED))
                .and(wrapper -> wrapper.isNull("next_retry_time").or().le("next_retry_time", now))
                .apply("retry_count < max_retries")
                .orderByAsc("id")
                .last("LIMIT " + BATCH_SIZE);
    }

    private void markOutboxProcessing(OutboxMessageEntity message) {
        message.setStatus(SyncerConstants.STATUS_PROCESSING);
        message.setUpdatedAt(LocalDateTime.now());
        outboxMessageMapper.updateById(message);
    }

    private SyncTaskEntity buildTask(OutboxMessageEntity message) {
        Map<String, Object> payload = parsePayload(message.getPayload());
        SyncTaskEntity task = new SyncTaskEntity();
        task.setTaskNo("ST-" + message.getMsgId());
        task.setSourceService(stringValue(payload.get("sourceService"), SyncerConstants.DEFAULT_SOURCE_SERVICE));
        task.setTargetService(stringValue(payload.get("targetService"), SyncerConstants.DEFAULT_TARGET_SERVICE));
        task.setPayload(message.getPayload());
        task.setStatus(SyncerConstants.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetries(message.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : message.getMaxRetries());
        return task;
    }

    private void markOutboxSuccess(OutboxMessageEntity message) {
        message.setStatus(SyncerConstants.STATUS_SUCCESS);
        message.setNextRetryTime(null);
        message.setUpdatedAt(LocalDateTime.now());
        outboxMessageMapper.updateById(message);
    }

    private void markOutboxFailure(OutboxMessageEntity message, Exception exception) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int nextRetryCount = retryCount + 1;
        int maxRetries = message.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : message.getMaxRetries();
        message.setRetryCount(nextRetryCount);
        message.setStatus(SyncerConstants.STATUS_FAILED);
        if (nextRetryCount < maxRetries) {
            message.setNextRetryTime(LocalDateTime.now().plusSeconds(RetryPolicyUtil.nextDelaySeconds(nextRetryCount)));
        }
        message.setUpdatedAt(LocalDateTime.now());
        outboxMessageMapper.updateById(message);
        LOGGER.warn("failed to convert outbox message {}: {}", message.getMsgId(), exception.getMessage());
    }

    private void executeTask(SyncTaskEntity task) {
        task.setStatus(SyncerConstants.STATUS_PROCESSING);
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);
        try {
            simulateDelivery(task);
            task.setStatus(SyncerConstants.STATUS_SUCCESS);
            task.setLastError(null);
            task.setNextRetryTime(null);
            task.setUpdatedAt(LocalDateTime.now());
            syncTaskMapper.updateById(task);
            markLatestRetrySuccess(task);
        } catch (Exception exception) {
            recordTaskFailure(task, exception);
        }
    }

    private void simulateDelivery(SyncTaskEntity task) {
        Map<String, Object> payload = parsePayload(task.getPayload());
        boolean alwaysFail = booleanValue(payload.get("alwaysFail"));
        boolean failFirstAttempt = booleanValue(payload.get("failFirstAttempt"));
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (alwaysFail || (failFirstAttempt && retryCount == 0)) {
            String bizType = stringValue(payload.get("bizType"), "UNKNOWN");
            String bizId = stringValue(payload.get("bizId"), "UNKNOWN");
            throw new IllegalStateException("demo delivery failed for " + bizType + ":" + bizId);
        }
    }

    private void recordTaskFailure(SyncTaskEntity task, Exception exception) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetryCount = retryCount + 1;
        int maxRetries = task.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : task.getMaxRetries();
        task.setRetryCount(nextRetryCount);
        task.setStatus(SyncerConstants.STATUS_FAILED);
        task.setLastError(shortError(exception));
        if (nextRetryCount < maxRetries) {
            task.setNextRetryTime(LocalDateTime.now().plusSeconds(RetryPolicyUtil.nextDelaySeconds(nextRetryCount)));
        } else {
            task.setNextRetryTime(null);
        }
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);

        RetryRecordEntity retry = new RetryRecordEntity();
        retry.setBizType(SyncerConstants.BIZ_TYPE_SYNC_TASK);
        retry.setBizId(task.getTaskNo());
        retry.setAttemptNo(nextRetryCount);
        retry.setStatus(nextRetryCount < maxRetries ? SyncerConstants.STATUS_PENDING : SyncerConstants.STATUS_FAILED);
        retry.setRetryTime(task.getNextRetryTime());
        retry.setErrorMessage(task.getLastError());
        retryRecordMapper.insert(retry);
    }

    private void markLatestRetrySuccess(SyncTaskEntity task) {
        RetryRecordEntity retry = retryRecordMapper.selectOne(new QueryWrapper<RetryRecordEntity>()
                .eq("biz_type", SyncerConstants.BIZ_TYPE_SYNC_TASK)
                .eq("biz_id", task.getTaskNo())
                .eq("status", SyncerConstants.STATUS_PENDING)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (retry == null) {
            return;
        }
        retry.setStatus(SyncerConstants.STATUS_SUCCESS);
        retry.setErrorMessage(null);
        retry.setUpdatedAt(LocalDateTime.now());
        retryRecordMapper.updateById(retry);
    }

    private Map<String, Object> parsePayload(String payload) {
        Map<String, Object> parsed = JsonUtil.fromJson(payload, new TypeReference<Map<String, Object>>() {});
        return parsed == null ? Map.of() : parsed;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String shortError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private Map<String, Long> statusCounts(String table) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if ("outbox_message".equals(table)) {
            counts.put(SyncerConstants.STATUS_NEW, outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>().eq("status", SyncerConstants.STATUS_NEW)));
            counts.put(SyncerConstants.STATUS_PROCESSING, outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>().eq("status", SyncerConstants.STATUS_PROCESSING)));
            counts.put(SyncerConstants.STATUS_SUCCESS, outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>().eq("status", SyncerConstants.STATUS_SUCCESS)));
            counts.put(SyncerConstants.STATUS_FAILED, outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>().eq("status", SyncerConstants.STATUS_FAILED)));
            return counts;
        }
        if ("sync_task".equals(table)) {
            counts.put(SyncerConstants.STATUS_PENDING, syncTaskMapper.selectCount(new QueryWrapper<SyncTaskEntity>().eq("status", SyncerConstants.STATUS_PENDING)));
            counts.put(SyncerConstants.STATUS_PROCESSING, syncTaskMapper.selectCount(new QueryWrapper<SyncTaskEntity>().eq("status", SyncerConstants.STATUS_PROCESSING)));
            counts.put(SyncerConstants.STATUS_SUCCESS, syncTaskMapper.selectCount(new QueryWrapper<SyncTaskEntity>().eq("status", SyncerConstants.STATUS_SUCCESS)));
            counts.put(SyncerConstants.STATUS_FAILED, syncTaskMapper.selectCount(new QueryWrapper<SyncTaskEntity>().eq("status", SyncerConstants.STATUS_FAILED)));
            return counts;
        }
        counts.put(SyncerConstants.STATUS_PENDING, retryRecordMapper.selectCount(new QueryWrapper<RetryRecordEntity>().eq("status", SyncerConstants.STATUS_PENDING)));
        counts.put(SyncerConstants.STATUS_SUCCESS, retryRecordMapper.selectCount(new QueryWrapper<RetryRecordEntity>().eq("status", SyncerConstants.STATUS_SUCCESS)));
        counts.put(SyncerConstants.STATUS_FAILED, retryRecordMapper.selectCount(new QueryWrapper<RetryRecordEntity>().eq("status", SyncerConstants.STATUS_FAILED)));
        return counts;
    }
}
