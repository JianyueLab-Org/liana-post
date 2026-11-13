package com.liana.post.transport.repository;

import com.liana.post.transport.model.dto.PageResult;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import com.liana.post.transport.model.entity.TransportTaskEntity;
import com.liana.post.transport.model.entity.TransportTaskBagEntity;

import java.util.Optional;
import java.util.List;

public interface TransportRepository {
    TransportAssetEntity saveAsset(TransportAssetEntity entity);
    TransportAssetEntity updateAsset(TransportAssetEntity entity);
    Optional<TransportAssetEntity> findAssetByCode(String code);
    PageResult<TransportAssetEntity> pageAssets(long page, long pageSize, String keyword, String status);

    TransportRouteEntity saveRoute(TransportRouteEntity entity);
    TransportRouteEntity updateRoute(TransportRouteEntity entity);
    Optional<TransportRouteEntity> findRouteByCode(String code);
    List<TransportRouteEntity> findRoutesByFacilityRouteCode(String code);
    Optional<TransportRouteEntity> findRouteById(Long id);
    PageResult<TransportRouteEntity> pageRoutes(long page, long pageSize, String keyword, String status, String transportType);

    TransportScheduleEntity saveSchedule(TransportScheduleEntity entity);
    TransportScheduleEntity updateSchedule(TransportScheduleEntity entity);
    Optional<TransportScheduleEntity> findScheduleByCode(String code);
    Optional<TransportScheduleEntity> findScheduleById(Long id);
    TransportScheduleEntity lockSchedule(Long id);
    List<TransportScheduleEntity> findFutureSchedulesByRouteId(Long routeId);
    List<TransportScheduleEntity> findOverlappingSchedules(Long assetId, java.time.LocalDateTime departure, java.time.LocalDateTime arrival, Long excludedId);
    Optional<TransportScheduleEntity> findScheduleByRouteId(Long routeId);
    PageResult<TransportScheduleEntity> pageSchedules(long page, long pageSize, String keyword, String status);

    TransportTaskEntity saveTask(TransportTaskEntity entity);
    TransportTaskEntity updateTask(TransportTaskEntity entity);
    Optional<TransportTaskEntity> findTaskByCode(String code);
    Optional<TransportTaskEntity> findTaskByDispatchBagId(Long dispatchBagId);
    List<TransportTaskEntity> findTasksByScheduleId(Long scheduleId);
    Optional<TransportTaskEntity> findActiveTaskByScheduleId(Long scheduleId);
    Optional<TransportTaskBagEntity> findTaskBagByDispatchBagId(Long dispatchBagId);
    List<TransportTaskBagEntity> findTaskBags(Long taskId);
    void addTaskBag(TransportTaskBagEntity bag);
    List<TransportTaskBagEntity> findUnlinkedTaskBags();
    void markTaskBagLinked(Long id);
    Optional<TransportTaskEntity> findTaskById(Long id);
    Optional<TransportAssetEntity> findAssetById(Long id);
    PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status);

    boolean hasAnyData();
    void seedDefaults();
}
