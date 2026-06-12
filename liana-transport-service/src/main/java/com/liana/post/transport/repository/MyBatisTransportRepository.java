package com.liana.post.transport.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.transport.constant.TransportConstants;
import com.liana.post.transport.mapper.TransportAssetMapper;
import com.liana.post.transport.mapper.TransportRouteMapper;
import com.liana.post.transport.mapper.TransportScheduleMapper;
import com.liana.post.transport.mapper.TransportTaskMapper;
import com.liana.post.transport.model.dto.PageResult;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import com.liana.post.transport.model.entity.TransportTaskEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MyBatisTransportRepository implements TransportRepository {
    private final TransportAssetMapper assetMapper;
    private final TransportRouteMapper routeMapper;
    private final TransportScheduleMapper scheduleMapper;
    private final TransportTaskMapper taskMapper;

    public MyBatisTransportRepository(TransportAssetMapper assetMapper, TransportRouteMapper routeMapper, TransportScheduleMapper scheduleMapper, TransportTaskMapper taskMapper) {
        this.assetMapper = assetMapper;
        this.routeMapper = routeMapper;
        this.scheduleMapper = scheduleMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    @Transactional
    public TransportAssetEntity saveAsset(TransportAssetEntity entity) {
        stamp(entity);
        if (assetMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to save transport asset");
        return entity;
    }

    @Override
    @Transactional
    public TransportAssetEntity updateAsset(TransportAssetEntity entity) {
        stamp(entity);
        if (assetMapper.updateById(entity) <= 0) throw new BusinessException(500, "failed to update transport asset");
        return entity;
    }

    @Override
    public Optional<TransportAssetEntity> findAssetByCode(String code) {
        return Optional.ofNullable(assetMapper.selectOne(new LambdaQueryWrapper<TransportAssetEntity>().eq(TransportAssetEntity::getCode, normalize(code))));
    }

    @Override
    public PageResult<TransportAssetEntity> pageAssets(long page, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<TransportAssetEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(TransportAssetEntity::getCode, kw).or().like(TransportAssetEntity::getName, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TransportAssetEntity::getStatus, status.trim());
        }
        Page<TransportAssetEntity> result = assetMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper.orderByDesc(TransportAssetEntity::getUpdatedAt));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    public TransportRouteEntity saveRoute(TransportRouteEntity entity) {
        stamp(entity);
        if (routeMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to save transport route");
        return entity;
    }

    @Override
    @Transactional
    public TransportRouteEntity updateRoute(TransportRouteEntity entity) {
        stamp(entity);
        if (routeMapper.updateById(entity) <= 0) throw new BusinessException(500, "failed to update transport route");
        return entity;
    }

    @Override
    public Optional<TransportRouteEntity> findRouteByCode(String code) {
        return Optional.ofNullable(routeMapper.selectOne(new LambdaQueryWrapper<TransportRouteEntity>().eq(TransportRouteEntity::getRouteCode, normalize(code))));
    }

    @Override
    public PageResult<TransportRouteEntity> pageRoutes(long page, long pageSize, String keyword, String status, String transportType) {
        LambdaQueryWrapper<TransportRouteEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.like(TransportRouteEntity::getRouteCode, kw);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TransportRouteEntity::getStatus, status.trim());
        }
        if (StringUtils.hasText(transportType)) {
            wrapper.eq(TransportRouteEntity::getTransportType, transportType.trim());
        }
        Page<TransportRouteEntity> result = routeMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper.orderByDesc(TransportRouteEntity::getUpdatedAt));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    public TransportScheduleEntity saveSchedule(TransportScheduleEntity entity) {
        stamp(entity);
        if (scheduleMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to save transport schedule");
        return entity;
    }

    @Override
    @Transactional
    public TransportScheduleEntity updateSchedule(TransportScheduleEntity entity) {
        stamp(entity);
        if (scheduleMapper.updateById(entity) <= 0) throw new BusinessException(500, "failed to update transport schedule");
        return entity;
    }

    @Override
    public Optional<TransportScheduleEntity> findScheduleByCode(String code) {
        return Optional.ofNullable(scheduleMapper.selectOne(new LambdaQueryWrapper<TransportScheduleEntity>().eq(TransportScheduleEntity::getScheduleCode, normalize(code))));
    }

    @Override
    public Optional<TransportScheduleEntity> findScheduleByRouteId(Long routeId) {
        if (routeId == null) {
            return Optional.empty();
        }
        return scheduleMapper.selectList(new LambdaQueryWrapper<TransportScheduleEntity>()
                        .eq(TransportScheduleEntity::getRouteId, routeId)
                        .orderByDesc(TransportScheduleEntity::getUpdatedAt))
                .stream()
                .findFirst();
    }

    @Override
    public PageResult<TransportScheduleEntity> pageSchedules(long page, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<TransportScheduleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(TransportScheduleEntity::getScheduleCode, kw).or().like(TransportScheduleEntity::getWeekday, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TransportScheduleEntity::getStatus, status.trim());
        }
        Page<TransportScheduleEntity> result = scheduleMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper.orderByDesc(TransportScheduleEntity::getUpdatedAt));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    public TransportTaskEntity saveTask(TransportTaskEntity entity) {
        if (entity.getTaskCode() == null || entity.getTaskCode().isBlank()) {
            entity.setTaskCode(IdGeneratorUtil.generateBizNo("TT"));
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(TransportConstants.TASK_STATUS_CREATED);
        }
        entity.setCreatedAt(LocalDateTime.now());
        if (taskMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to save transport task");
        return entity;
    }

    @Override
    @Transactional
    public TransportTaskEntity updateTask(TransportTaskEntity entity) {
        if (entity.getTaskCode() == null || entity.getTaskCode().isBlank()) {
            throw new BusinessException(400, "taskCode cannot be blank");
        }
        if (taskMapper.updateById(entity) <= 0) throw new BusinessException(500, "failed to update transport task");
        return entity;
    }

    @Override
    public Optional<TransportTaskEntity> findTaskByCode(String code) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<TransportTaskEntity>().eq(TransportTaskEntity::getTaskCode, normalize(code))));
    }

    @Override
    public Optional<TransportTaskEntity> findTaskByDispatchBagId(Long dispatchBagId) {
        if (dispatchBagId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<TransportTaskEntity>().eq(TransportTaskEntity::getDispatchBagId, dispatchBagId)));
    }

    @Override
    public PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<TransportTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.like(TransportTaskEntity::getTaskCode, kw);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TransportTaskEntity::getStatus, status.trim());
        }
        Page<TransportTaskEntity> result = taskMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper.orderByDesc(TransportTaskEntity::getCreatedAt));
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public boolean hasAnyData() {
        return assetMapper.selectCount(null) > 0
                || routeMapper.selectCount(null) > 0
                || scheduleMapper.selectCount(null) > 0
                || taskMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        if (hasAnyData()) {
            return;
        }
        TransportAssetEntity asset = new TransportAssetEntity();
        asset.setCode("T-001");
        asset.setName("Post Truck 12");
        asset.setType(TransportConstants.TYPE_TRUCK);
        asset.setCapacity(new java.math.BigDecimal("80"));
        asset.setStatus(TransportConstants.STATUS_AVAILABLE);
        saveAsset(asset);
    }

    private void stamp(TransportAssetEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());
    }

    private void stamp(TransportRouteEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());
    }

    private void stamp(TransportScheduleEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) entity.setCreatedAt(LocalDateTime.now());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) throw new BusinessException(400, "code cannot be blank");
        return value.trim();
    }
}
