package com.liana.post.transport.repository;

import com.liana.post.transport.model.dto.PageResult;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import com.liana.post.transport.model.entity.TransportTaskEntity;

import java.util.Optional;

public interface TransportRepository {
    TransportAssetEntity saveAsset(TransportAssetEntity entity);
    TransportAssetEntity updateAsset(TransportAssetEntity entity);
    Optional<TransportAssetEntity> findAssetByCode(String code);
    PageResult<TransportAssetEntity> pageAssets(long page, long pageSize, String keyword, String status);

    TransportRouteEntity saveRoute(TransportRouteEntity entity);
    TransportRouteEntity updateRoute(TransportRouteEntity entity);
    Optional<TransportRouteEntity> findRouteByCode(String code);
    PageResult<TransportRouteEntity> pageRoutes(long page, long pageSize, String keyword, String status, String transportType);

    TransportScheduleEntity saveSchedule(TransportScheduleEntity entity);
    TransportScheduleEntity updateSchedule(TransportScheduleEntity entity);
    Optional<TransportScheduleEntity> findScheduleByCode(String code);
    Optional<TransportScheduleEntity> findScheduleByRouteId(Long routeId);
    PageResult<TransportScheduleEntity> pageSchedules(long page, long pageSize, String keyword, String status);

    TransportTaskEntity saveTask(TransportTaskEntity entity);
    TransportTaskEntity updateTask(TransportTaskEntity entity);
    Optional<TransportTaskEntity> findTaskByCode(String code);
    Optional<TransportTaskEntity> findTaskByDispatchBagId(Long dispatchBagId);
    PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status);

    boolean hasAnyData();
    void seedDefaults();
}
