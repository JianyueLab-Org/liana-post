package com.liana.post.tracking.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.tracking.constant.TrackingConstants;
import com.liana.post.tracking.mapper.TrackingEventMapper;
import com.liana.post.tracking.model.entity.TrackingEventEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTrackingRepository implements TrackingRepository {

    private final TrackingEventMapper trackingEventMapper;

    public MyBatisTrackingRepository(TrackingEventMapper trackingEventMapper) {
        this.trackingEventMapper = trackingEventMapper;
    }

    @Override
    @Transactional
    public TrackingEventEntity save(TrackingEventEntity entity) {
        if (entity.getEventNo() == null || entity.getEventNo().isBlank()) {
            entity.setEventNo(IdGeneratorUtil.generateTrackingEventNo());
        }
        if (entity.getEventTime() == null) {
            entity.setEventTime(LocalDateTime.now());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        if (trackingEventMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save tracking event");
        }
        return entity;
    }

    @Override
    public Optional<TrackingEventEntity> findByEventNo(String eventNo) {
        return Optional.ofNullable(trackingEventMapper.selectOne(new LambdaQueryWrapper<TrackingEventEntity>().eq(TrackingEventEntity::getEventNo, normalize(eventNo))));
    }

    @Override
    public List<TrackingEventEntity> findAll() {
        return trackingEventMapper.selectList(null);
    }

    @Override
    public List<TrackingEventEntity> findByWaybillNo(String waybillNo) {
        return trackingEventMapper.selectList(new LambdaQueryWrapper<TrackingEventEntity>()
                .eq(TrackingEventEntity::getWaybillNo, normalize(waybillNo))
                .orderByAsc(TrackingEventEntity::getEventTime));
    }

    @Override
    public List<TrackingEventEntity> findByWaybillNoAndFilters(String waybillNo, String eventType, String sourceService, String facilityCode) {
        LambdaQueryWrapper<TrackingEventEntity> wrapper = new LambdaQueryWrapper<TrackingEventEntity>()
                .eq(TrackingEventEntity::getWaybillNo, normalize(waybillNo));
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(TrackingEventEntity::getEventType, eventType.trim());
        }
        if (sourceService != null && !sourceService.isBlank()) {
            wrapper.eq(TrackingEventEntity::getSourceService, sourceService.trim());
        }
        if (facilityCode != null && !facilityCode.isBlank()) {
            wrapper.eq(TrackingEventEntity::getFacilityCode, facilityCode.trim());
        }
        return trackingEventMapper.selectList(wrapper.orderByAsc(TrackingEventEntity::getEventTime));
    }

    @Override
    public boolean hasAnyData() {
        return trackingEventMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        if (hasAnyData()) {
            return;
        }
        TrackingEventEntity entity = new TrackingEventEntity();
        entity.setWaybillNo(com.liana.post.common.util.UpuBarcodeUtil.generateRandom("CP"));
        entity.setEventType(TrackingConstants.EVENT_ACCEPTED);
        entity.setSourceService(TrackingConstants.SOURCE_OMS);
        entity.setFacilityCode("B1");
        entity.setFacilityName("Demo Facility");
        entity.setPayload("{}");
        save(entity);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "waybillNo cannot be blank");
        }
        return value.trim().toUpperCase();
    }
}
