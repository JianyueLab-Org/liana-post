package com.liana.post.dispatch.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.dispatch.cache.RedisCacheSupport;
import com.liana.post.dispatch.cache.RedisKeyConstants;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.dispatch.constant.DispatchConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.liana.post.dispatch.mapper.DispatchBagMapper;
import com.liana.post.dispatch.mapper.DispatchBatchMapper;
import com.liana.post.dispatch.mapper.HandoffRecordMapper;
import com.liana.post.dispatch.mapper.RouteRuleMapper;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.model.entity.HandoffRecordEntity;
import com.liana.post.dispatch.model.entity.RouteRuleEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MyBatisDispatchRepository implements DispatchRepository {

    private static final Set<String> ALLOWED_BATCH_STATUS = Set.of(
            DispatchConstants.BATCH_STATUS_PENDING,
            DispatchConstants.BATCH_STATUS_APPROVED,
            DispatchConstants.BATCH_STATUS_HANDED_OFF
    );

    private static final Set<String> ALLOWED_BAG_STATUS = Set.of(
            DispatchConstants.BAG_STATUS_CREATED,
            DispatchConstants.BAG_STATUS_REVIEWED,
            DispatchConstants.BAG_STATUS_DISPATCHED
    );

    private static final Set<String> ALLOWED_HANDOFF_STATUS = Set.of(
            DispatchConstants.HANDAOFF_STATUS_PENDING,
            DispatchConstants.HANDAOFF_STATUS_COMPLETED
    );

    private final RouteRuleMapper routeRuleMapper;
    private final DispatchBagMapper dispatchBagMapper;
    private final DispatchBatchMapper dispatchBatchMapper;
    private final HandoffRecordMapper handoffRecordMapper;
    private final StringRedisTemplate redisTemplate;

    public MyBatisDispatchRepository(RouteRuleMapper routeRuleMapper, DispatchBagMapper dispatchBagMapper, DispatchBatchMapper dispatchBatchMapper, HandoffRecordMapper handoffRecordMapper, StringRedisTemplate redisTemplate) {
        this.routeRuleMapper = routeRuleMapper;
        this.dispatchBagMapper = dispatchBagMapper;
        this.dispatchBatchMapper = dispatchBatchMapper;
        this.handoffRecordMapper = handoffRecordMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public RouteRuleEntity saveRouteRule(RouteRuleEntity entity) {
        stamp(entity);
        if (routeRuleMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save route rule");
        }
        return entity;
    }

    @Override
    @Transactional
    public DispatchBagEntity saveDispatchBag(DispatchBagEntity entity) {
        stamp(entity);
        if (entity.getBagNo() == null || entity.getBagNo().isBlank()) {
            entity.setBagNo(IdGeneratorUtil.generateBagNo());
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DispatchConstants.BAG_STATUS_CREATED);
        }
        if (!ALLOWED_BAG_STATUS.contains(entity.getStatus())) {
            throw new BusinessException(400, "unsupported bag status");
        }
        int affected = entity.getId() == null ? dispatchBagMapper.insert(entity) : dispatchBagMapper.updateById(entity);
        if (affected <= 0) {
            throw new BusinessException(500, "failed to save dispatch bag");
        }
        return entity;
    }

    @Override
    @Transactional
    public DispatchBagEntity updateDispatchBag(DispatchBagEntity entity) {
        if (entity.getId() == null) {
            throw new BusinessException(400, "dispatch bag id cannot be blank");
        }
        if (entity.getBagNo() == null || entity.getBagNo().isBlank()) {
            throw new BusinessException(400, "bagNo cannot be blank");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DispatchConstants.BAG_STATUS_CREATED);
        }
        if (!ALLOWED_BAG_STATUS.contains(entity.getStatus())) {
            throw new BusinessException(400, "unsupported bag status");
        }
        stamp(entity);
        if (dispatchBagMapper.updateById(entity) <= 0) {
            throw new BusinessException(500, "failed to update dispatch bag");
        }
        return entity;
    }

    @Override
    @Transactional
    public DispatchBatchEntity saveDispatchBatch(DispatchBatchEntity entity) {
        stamp(entity);
        if (entity.getBatchNo() == null || entity.getBatchNo().isBlank()) {
            entity.setBatchNo(IdGeneratorUtil.generateDispatchNo());
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        }
        if (!ALLOWED_BATCH_STATUS.contains(entity.getStatus())) {
            throw new BusinessException(400, "unsupported batch status");
        }
        if (dispatchBatchMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save dispatch batch");
        }
        return entity;
    }

    @Override
    @Transactional
    public DispatchBatchEntity updateDispatchBatch(DispatchBatchEntity entity) {
        if (entity.getId() == null) {
            throw new BusinessException(400, "dispatch batch id cannot be blank");
        }
        if (entity.getBatchNo() == null || entity.getBatchNo().isBlank()) {
            throw new BusinessException(400, "batchNo cannot be blank");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        }
        if (!ALLOWED_BATCH_STATUS.contains(entity.getStatus())) {
            throw new BusinessException(400, "unsupported batch status");
        }
        stamp(entity);
        if (dispatchBatchMapper.updateById(entity) <= 0) {
            throw new BusinessException(500, "failed to update dispatch batch");
        }
        return entity;
    }

    @Override
    @Transactional
    public HandoffRecordEntity saveHandoffRecord(HandoffRecordEntity entity) {
        stamp(entity);
        if (entity.getHandoffNo() == null || entity.getHandoffNo().isBlank()) {
            entity.setHandoffNo(IdGeneratorUtil.generateBizNo("H"));
        }
        if (entity.getHandoffTime() == null) {
            entity.setHandoffTime(LocalDateTime.now());
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DispatchConstants.HANDAOFF_STATUS_PENDING);
        }
        if (!ALLOWED_HANDOFF_STATUS.contains(entity.getStatus())) {
            throw new BusinessException(400, "unsupported handoff status");
        }
        if (handoffRecordMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save handoff record");
        }
        return entity;
    }

    @Override
    public Optional<RouteRuleEntity> findRouteRuleByCode(String ruleCode) {
        String cacheKey = RedisKeyConstants.DISPATCH_BAG_PREFIX + "route:" + normalize(ruleCode);
        RouteRuleEntity cached = RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, RouteRuleEntity.class, java.time.Duration.ofMinutes(10), () -> routeRuleMapper.selectOne(new LambdaQueryWrapper<RouteRuleEntity>().eq(RouteRuleEntity::getRuleCode, normalize(ruleCode))));
        return Optional.ofNullable(cached);
    }

    @Override
    public Optional<RouteRuleEntity> findBestRouteRule(String sourceFacilityCode, String targetFacilityCode) {
        return routeRuleMapper.selectList(new LambdaQueryWrapper<RouteRuleEntity>()
                .eq(RouteRuleEntity::getSourceFacilityCode, normalize(sourceFacilityCode))
                .eq(RouteRuleEntity::getTargetFacilityCode, normalize(targetFacilityCode))
                .eq(RouteRuleEntity::getEnabled, 1)
                .orderByAsc(RouteRuleEntity::getPriorityLevel)).stream().findFirst();
    }

    @Override
    public Optional<RouteRuleEntity> findBestRouteRule(String sourceFacilityCode, String targetFacilityCode, String routeScope, String destCountryCode) {
        LambdaQueryWrapper<RouteRuleEntity> query = new LambdaQueryWrapper<RouteRuleEntity>()
                .eq(RouteRuleEntity::getSourceFacilityCode, normalize(sourceFacilityCode))
                .eq(RouteRuleEntity::getEnabled, 1);
        if (targetFacilityCode != null && !targetFacilityCode.isBlank()) {
            query.eq(RouteRuleEntity::getTargetFacilityCode, normalize(targetFacilityCode));
        }
        if (routeScope != null && !routeScope.isBlank()) {
            query.eq(RouteRuleEntity::getRouteScope, normalize(routeScope));
        }
        if (destCountryCode != null && !destCountryCode.isBlank()) {
            query.eq(RouteRuleEntity::getDestCountryCode, normalize(destCountryCode));
        }
        return routeRuleMapper.selectList(query.orderByAsc(RouteRuleEntity::getPriorityLevel)).stream().findFirst();
    }

    @Override
    public Optional<DispatchBagEntity> findDispatchBagById(Long id) {
        return Optional.ofNullable(dispatchBagMapper.selectById(id));
    }

    @Override
    @Transactional
    public DispatchBagEntity linkTransportTask(Long id, String transportTaskCode) {
        DispatchBagEntity bag = dispatchBagMapper.selectById(id);
        if (bag == null) {
            throw new BusinessException(404, "dispatch bag not found: " + id);
        }
        bag.setTransportTaskCode(transportTaskCode);
        stamp(bag);
        if (dispatchBagMapper.updateById(bag) <= 0) {
            throw new BusinessException(500, "failed to link transport task");
        }
        return bag;
    }

    @Override
    public Optional<DispatchBagEntity> findDispatchBagByBagNo(String bagNo) {
        String cacheKey = RedisKeyConstants.DISPATCH_BAG_PREFIX + normalize(bagNo);
        DispatchBagEntity cached = RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, DispatchBagEntity.class, java.time.Duration.ofMinutes(5), () -> dispatchBagMapper.selectOne(new LambdaQueryWrapper<DispatchBagEntity>().eq(DispatchBagEntity::getBagNo, normalize(bagNo))));
        return Optional.ofNullable(cached);
    }

    @Override
    public Optional<DispatchBatchEntity> findDispatchBatchByBatchNo(String batchNo) {
        return Optional.ofNullable(dispatchBatchMapper.selectOne(new LambdaQueryWrapper<DispatchBatchEntity>().eq(DispatchBatchEntity::getBatchNo, normalize(batchNo))));
    }

    @Override
    public Optional<DispatchBatchEntity> findDispatchBatchByBagNo(String bagNo) {
        return Optional.ofNullable(dispatchBatchMapper.selectOne(new LambdaQueryWrapper<DispatchBatchEntity>().eq(DispatchBatchEntity::getBagNo, normalize(bagNo))));
    }

    @Override
    public Optional<HandoffRecordEntity> findHandoffRecordByHandoffNo(String handoffNo) {
        return Optional.ofNullable(handoffRecordMapper.selectOne(new LambdaQueryWrapper<HandoffRecordEntity>().eq(HandoffRecordEntity::getHandoffNo, normalize(handoffNo))));
    }

    @Override
    public Optional<HandoffRecordEntity> findHandoffRecordByBagNo(String bagNo) {
        return Optional.ofNullable(handoffRecordMapper.selectOne(new LambdaQueryWrapper<HandoffRecordEntity>()
                .eq(HandoffRecordEntity::getBagNo, normalize(bagNo))
                .eq(HandoffRecordEntity::getStatus, DispatchConstants.HANDAOFF_STATUS_COMPLETED)
                .last("LIMIT 1")));
    }

    @Override
    public List<DispatchBagEntity> findAllDispatchBags() {
        return dispatchBagMapper.selectList(null);
    }

    @Override
    public List<DispatchBatchEntity> findAllDispatchBatches() {
        return dispatchBatchMapper.selectList(null);
    }

    @Override
    public List<HandoffRecordEntity> findAllHandoffRecords() {
        return handoffRecordMapper.selectList(null);
    }

    @Override
    @Transactional
    public int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode) {
        RedisCacheSupport.evictLike(redisTemplate, RedisKeyConstants.DISPATCH_BAG_PREFIX);
        return waybillNos.size();
    }

    @Override
    public boolean hasAnyData() {
        return routeRuleMapper.selectCount(null) > 0
                || dispatchBagMapper.selectCount(null) > 0
                || dispatchBatchMapper.selectCount(null) > 0
                || handoffRecordMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        if (hasAnyData()) {
            return;
        }
        RouteRuleEntity rule = new RouteRuleEntity();
        rule.setRuleCode("B1-C1-TRUCK");
        rule.setSourceFacilityCode("B1");
        rule.setTargetFacilityCode("C1");
        rule.setRouteScope("DOMESTIC");
        rule.setTransportMode("TRUCK");
        rule.setPriorityLevel(1);
        rule.setEnabled(1);
        saveRouteRule(rule);
    }

    private void stamp(RouteRuleEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(DispatchBagEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(DispatchBatchEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(HandoffRecordEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim();
    }
}
