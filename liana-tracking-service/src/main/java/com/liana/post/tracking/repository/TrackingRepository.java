
package com.liana.post.tracking.repository;

import com.liana.post.tracking.model.entity.TrackingEventEntity;

import java.util.List;
import java.util.Optional;

public interface TrackingRepository {
    TrackingEventEntity save(TrackingEventEntity entity);
    Optional<TrackingEventEntity> findByEventNo(String eventNo);
    List<TrackingEventEntity> findAll();
    List<TrackingEventEntity> findByWaybillNo(String waybillNo);
    List<TrackingEventEntity> findByWaybillNoAndFilters(String waybillNo, String eventType, String sourceService, String facilityCode);
    boolean hasAnyData();
    void seedDefaults();
}
