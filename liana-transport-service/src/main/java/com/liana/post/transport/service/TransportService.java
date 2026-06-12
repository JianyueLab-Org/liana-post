package com.liana.post.transport.service;

import com.liana.post.transport.model.dto.PageResult;
import com.liana.post.transport.model.dto.TransportAssetRequest;
import com.liana.post.transport.model.dto.TransportRouteRequest;
import com.liana.post.transport.model.dto.TransportScheduleRequest;
import com.liana.post.transport.model.dto.TransportTaskRequest;
import com.liana.post.transport.model.dto.TransportTaskStatusRequest;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import com.liana.post.transport.model.entity.TransportTaskEntity;

public interface TransportService {
    TransportAssetEntity createAsset(TransportAssetRequest request);
    TransportAssetEntity updateAsset(String code, TransportAssetRequest request);
    TransportAssetEntity getAsset(String code);
    PageResult<TransportAssetEntity> pageAssets(long page, long pageSize, String keyword, String status);

    TransportRouteEntity createRoute(TransportRouteRequest request);
    TransportRouteEntity updateRoute(String routeCode, TransportRouteRequest request);
    TransportRouteEntity getRoute(String routeCode);
    PageResult<TransportRouteEntity> pageRoutes(long page, long pageSize, String keyword, String status, String transportType);

    TransportScheduleEntity createSchedule(TransportScheduleRequest request);
    TransportScheduleEntity updateSchedule(String scheduleCode, TransportScheduleRequest request);
    TransportScheduleEntity getSchedule(String scheduleCode);
    PageResult<TransportScheduleEntity> pageSchedules(long page, long pageSize, String keyword, String status);

    TransportTaskEntity createTask(TransportTaskRequest request);
    TransportTaskEntity createTaskFromDispatchBag(Long dispatchBagId);
    TransportTaskEntity updateTaskStatus(String taskCode, TransportTaskStatusRequest request);
    TransportTaskEntity getTask(String taskCode);
    PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status);

    void initDefaults();
}
