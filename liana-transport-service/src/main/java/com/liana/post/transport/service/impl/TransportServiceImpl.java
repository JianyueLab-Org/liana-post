package com.liana.post.transport.service.impl;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.transport.constant.TransportConstants;
import com.liana.post.transport.client.DispatchClient;
import com.liana.post.transport.client.TrackingClient;
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
import com.liana.post.transport.repository.TransportRepository;
import com.liana.post.transport.service.TransportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportServiceImpl implements TransportService {
    private final TransportRepository transportRepository;
    private final ObjectProvider<DispatchClient> dispatchClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;

    public TransportServiceImpl(TransportRepository transportRepository, ObjectProvider<DispatchClient> dispatchClientProvider, ObjectProvider<TrackingClient> trackingClientProvider) {
        this.transportRepository = transportRepository;
        this.dispatchClientProvider = dispatchClientProvider;
        this.trackingClientProvider = trackingClientProvider;
    }

    @Override
    public TransportAssetEntity createAsset(TransportAssetRequest request) {
        validateAsset(request);
        if (transportRepository.findAssetByCode(request.getCode()).isPresent()) {
            throw new BusinessException(409, "transport asset already exists: " + request.getCode());
        }
        TransportAssetEntity entity = new TransportAssetEntity();
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setType(normalizeAssetType(request.getType()));
        entity.setCapacity(request.getCapacity());
        entity.setStatus(normalizeAssetStatus(request.getStatus()));
        return transportRepository.saveAsset(entity);
    }

    @Override
    public TransportAssetEntity updateAsset(String code, TransportAssetRequest request) {
        TransportAssetEntity entity = transportRepository.findAssetByCode(code)
                .orElseThrow(() -> new BusinessException(404, "transport asset not found: " + code));
        validateAsset(request);
        entity.setName(request.getName().trim());
        entity.setType(normalizeAssetType(request.getType()));
        entity.setCapacity(request.getCapacity());
        entity.setStatus(normalizeAssetStatus(request.getStatus()));
        return transportRepository.updateAsset(entity);
    }

    @Override
    public TransportAssetEntity getAsset(String code) {
        return transportRepository.findAssetByCode(code)
                .orElseThrow(() -> new BusinessException(404, "transport asset not found: " + code));
    }

    @Override
    public PageResult<TransportAssetEntity> pageAssets(long page, long pageSize, String keyword, String status) {
        return transportRepository.pageAssets(page, pageSize, keyword, status);
    }

    @Override
    public TransportRouteEntity createRoute(TransportRouteRequest request) {
        validateRoute(request);
        if (transportRepository.findRouteByCode(request.getRouteCode()).isPresent()) {
            throw new BusinessException(409, "transport route already exists: " + request.getRouteCode());
        }
        TransportRouteEntity entity = new TransportRouteEntity();
        entity.setRouteCode(request.getRouteCode().trim());
        entity.setOriginFacilityId(parseFacilityRef(request.getOriginFacilityCode()));
        entity.setDestinationFacilityId(parseFacilityRef(request.getDestinationFacilityCode()));
        entity.setTransportType(normalizeRouteType(request.getTransportType()));
        entity.setEstimatedHours(request.getEstimatedHours());
        entity.setStatus(normalizeRouteStatus(request.getStatus()));
        return transportRepository.saveRoute(entity);
    }

    @Override
    public TransportRouteEntity updateRoute(String routeCode, TransportRouteRequest request) {
        TransportRouteEntity entity = transportRepository.findRouteByCode(routeCode)
                .orElseThrow(() -> new BusinessException(404, "transport route not found: " + routeCode));
        validateRoute(request);
        entity.setOriginFacilityId(parseFacilityRef(request.getOriginFacilityCode()));
        entity.setDestinationFacilityId(parseFacilityRef(request.getDestinationFacilityCode()));
        entity.setTransportType(normalizeRouteType(request.getTransportType()));
        entity.setEstimatedHours(request.getEstimatedHours());
        entity.setStatus(normalizeRouteStatus(request.getStatus()));
        return transportRepository.updateRoute(entity);
    }

    @Override
    public TransportRouteEntity getRoute(String routeCode) {
        return transportRepository.findRouteByCode(routeCode)
                .orElseThrow(() -> new BusinessException(404, "transport route not found: " + routeCode));
    }

    @Override
    public PageResult<TransportRouteEntity> pageRoutes(long page, long pageSize, String keyword, String status, String transportType) {
        return transportRepository.pageRoutes(page, pageSize, keyword, status, transportType);
    }

    @Override
    public TransportScheduleEntity createSchedule(TransportScheduleRequest request) {
        validateSchedule(request);
        if (transportRepository.findScheduleByCode(request.getScheduleCode()).isPresent()) {
            throw new BusinessException(409, "transport schedule already exists: " + request.getScheduleCode());
        }
        TransportScheduleEntity entity = new TransportScheduleEntity();
        entity.setScheduleCode(request.getScheduleCode().trim());
        entity.setAssetId(request.getAssetId());
        entity.setRouteId(request.getRouteId());
        entity.setDepartureTime(request.getDepartureTime());
        entity.setArrivalTime(request.getArrivalTime());
        entity.setWeekday(request.getWeekday().trim());
        entity.setStatus(normalizeScheduleStatus(request.getStatus()));
        return transportRepository.saveSchedule(entity);
    }

    @Override
    public TransportScheduleEntity updateSchedule(String scheduleCode, TransportScheduleRequest request) {
        TransportScheduleEntity entity = transportRepository.findScheduleByCode(scheduleCode)
                .orElseThrow(() -> new BusinessException(404, "transport schedule not found: " + scheduleCode));
        validateSchedule(request);
        entity.setAssetId(request.getAssetId());
        entity.setRouteId(request.getRouteId());
        entity.setDepartureTime(request.getDepartureTime());
        entity.setArrivalTime(request.getArrivalTime());
        entity.setWeekday(request.getWeekday().trim());
        entity.setStatus(normalizeScheduleStatus(request.getStatus()));
        return transportRepository.updateSchedule(entity);
    }

    @Override
    public TransportScheduleEntity getSchedule(String scheduleCode) {
        return transportRepository.findScheduleByCode(scheduleCode)
                .orElseThrow(() -> new BusinessException(404, "transport schedule not found: " + scheduleCode));
    }

    @Override
    public PageResult<TransportScheduleEntity> pageSchedules(long page, long pageSize, String keyword, String status) {
        return transportRepository.pageSchedules(page, pageSize, keyword, status);
    }

    @Override
    @Transactional
    public TransportTaskEntity createTask(TransportTaskRequest request) {
        validateTask(request);
        TransportTaskEntity entity = new TransportTaskEntity();
        entity.setTaskCode(StringUtils.hasText(request.getTaskCode()) ? request.getTaskCode().trim() : IdGeneratorUtil.generateBizNo("TT"));
        entity.setDispatchBagId(request.getDispatchBagId());
        entity.setAssetId(request.getAssetId());
        entity.setRouteId(request.getRouteId());
        entity.setScheduleId(request.getScheduleId());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? normalizeTaskStatus(request.getStatus()) : TransportConstants.TASK_STATUS_CREATED);
        entity = transportRepository.saveTask(entity);
        linkDispatchBag(entity.getDispatchBagId(), entity.getTaskCode());
        recordTrackingEvent(entity.getDispatchBagId(), entity.getTaskCode(), TransportConstants.TASK_STATUS_CREATED, "task created");
        return entity;
    }

    @Override
    @Transactional
    public TransportTaskEntity createTaskFromDispatchBag(Long dispatchBagId) {
        if (dispatchBagId == null) {
            throw new BusinessException(400, "dispatchBagId is required");
        }
        DispatchBagBriefResponse bag = loadDispatchBag(dispatchBagId);
        if (transportRepository.findTaskByDispatchBagId(dispatchBagId).isPresent()) {
            throw new BusinessException(409, "transport task already exists for dispatch bag: " + dispatchBagId);
        }
        TransportTaskEntity entity = new TransportTaskEntity();
        entity.setTaskCode(IdGeneratorUtil.generateBizNo("TT"));
        entity.setDispatchBagId(dispatchBagId);
        Long routeId = findRouteIdByCode(bag.getRouteCode());
        entity.setRouteId(routeId);
        entity.setScheduleId(findScheduleIdByRouteId(routeId));
        entity.setAssetId(findAvailableAssetId(routeId));
        entity.setStatus(TransportConstants.TASK_STATUS_CREATED);
        entity = transportRepository.saveTask(entity);
        linkDispatchBag(dispatchBagId, entity.getTaskCode());
        recordTrackingEvent(dispatchBagId, entity.getTaskCode(), TransportConstants.TASK_STATUS_CREATED, "task created from dispatch bag");
        return entity;
    }

    @Override
    @Transactional
    public TransportTaskEntity updateTaskStatus(String taskCode, TransportTaskStatusRequest request) {
        TransportTaskEntity entity = transportRepository.findTaskByCode(taskCode)
                .orElseThrow(() -> new BusinessException(404, "transport task not found: " + taskCode));
        String newStatus = normalizeTaskStatus(request.getStatus());
        entity.setStatus(newStatus);
        entity = transportRepository.updateTask(entity);
        recordTrackingEvent(entity.getDispatchBagId(), entity.getTaskCode(), newStatus, "task status updated");
        return entity;
    }

    @Override
    public TransportTaskEntity getTask(String taskCode) {
        return transportRepository.findTaskByCode(taskCode)
                .orElseThrow(() -> new BusinessException(404, "transport task not found: " + taskCode));
    }

    @Override
    public PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status) {
        return transportRepository.pageTasks(page, pageSize, keyword, status);
    }

    @Override
    public void initDefaults() {
        transportRepository.seedDefaults();
    }

    private void validateAsset(TransportAssetRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode()) || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(400, "asset code and name are required");
        }
    }

    private void validateRoute(TransportRouteRequest request) {
        if (request == null || !StringUtils.hasText(request.getRouteCode())) {
            throw new BusinessException(400, "routeCode is required");
        }
    }

    private void validateSchedule(TransportScheduleRequest request) {
        if (request == null || !StringUtils.hasText(request.getScheduleCode())) {
            throw new BusinessException(400, "scheduleCode is required");
        }
    }

    private void validateTask(TransportTaskRequest request) {
        if (request == null || request.getDispatchBagId() == null) {
            throw new BusinessException(400, "dispatchBagId is required");
        }
    }

    private DispatchBagBriefResponse loadDispatchBag(Long dispatchBagId) {
        DispatchClient client = dispatchClientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException(503, "dispatch client unavailable");
        }
        return client.getBagById(dispatchBagId).getData();
    }

    private Long findRouteIdByCode(String routeCode) {
        if (!StringUtils.hasText(routeCode)) {
            return null;
        }
        TransportRouteEntity route = transportRepository.findRouteByCode(routeCode.trim()).orElse(null);
        return route == null ? null : route.getId();
    }

    private Long findScheduleIdByRouteId(Long routeId) {
        return transportRepository.findScheduleByRouteId(routeId).map(TransportScheduleEntity::getId).orElse(null);
    }

    private Long findAvailableAssetId(Long routeId) {
        return transportRepository.pageAssets(1, 20, null, TransportConstants.STATUS_AVAILABLE)
                .getList().stream()
                .map(TransportAssetEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private void linkDispatchBag(Long dispatchBagId, String taskCode) {
        DispatchClient client = dispatchClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        DispatchTransportTaskLinkRequest request = new DispatchTransportTaskLinkRequest();
        request.setTransportTaskCode(taskCode);
        client.linkTransportTask(dispatchBagId, request);
    }

    private void recordTrackingEvent(Long dispatchBagId, String taskCode, String status, String note) {
        TrackingClient client = trackingClientProvider.getIfAvailable();
        if (client == null || dispatchBagId == null) {
            return;
        }
        DispatchBagBriefResponse bag = loadDispatchBag(dispatchBagId);
        TrackingEventCreateRequest request = new TrackingEventCreateRequest();
        request.setWaybillNo(bag.getBagNo());
        request.setEventType(mapStatusToEventType(status));
        request.setSourceService("TRANSPORT");
        request.setFacilityCode(bag.getOriginFacilityCode());
        request.setFacilityName(null);
        request.setPayload("{\"taskCode\":\"" + taskCode + "\",\"dispatchBagId\":" + dispatchBagId + ",\"note\":\"" + note + "\"}");
        client.recordEvent(request);
    }

    private String mapStatusToEventType(String status) {
        if (TransportConstants.TASK_STATUS_CREATED.equals(status) || TransportConstants.TASK_STATUS_ASSIGNED.equals(status)) {
            return "TRANSPORT_ASSIGNED";
        }
        if (TransportConstants.TASK_STATUS_DEPARTED.equals(status)) {
            return "DEPARTED";
        }
        if (TransportConstants.TASK_STATUS_IN_TRANSIT.equals(status)) {
            return "IN_TRANSIT";
        }
        if (TransportConstants.TASK_STATUS_ARRIVED.equals(status)) {
            return "ARRIVED_AT_HUB";
        }
        if (TransportConstants.TASK_STATUS_COMPLETED.equals(status)) {
            return "COMPLETED";
        }
        return "TRANSPORT_ASSIGNED";
    }

    private Long findAssetIdByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        TransportAssetEntity asset = transportRepository.findAssetByCode(code.trim()).orElse(null);
        return asset == null ? null : asset.getId();
    }

    private String normalizeAssetType(String value) {
        String normalized = normalize(value);
        if (TransportConstants.TYPE_SHIP.equals(normalized)
                || TransportConstants.TYPE_AIRCRAFT.equals(normalized)
                || TransportConstants.TYPE_TRUCK.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported asset type");
    }

    private String normalizeAssetStatus(String value) {
        String normalized = normalize(value);
        if (TransportConstants.STATUS_AVAILABLE.equals(normalized)
                || TransportConstants.STATUS_IN_SERVICE.equals(normalized)
                || TransportConstants.STATUS_MAINTENANCE.equals(normalized)
                || TransportConstants.STATUS_RETIRED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported asset status");
    }

    private String normalizeRouteType(String value) {
        String normalized = normalize(value);
        if (TransportConstants.ROUTE_TYPE_SEA.equals(normalized)
                || TransportConstants.ROUTE_TYPE_AIR.equals(normalized)
                || TransportConstants.ROUTE_TYPE_LAND.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported route transport type");
    }

    private String normalizeRouteStatus(String value) {
        String normalized = normalize(value);
        if ("ACTIVE".equals(normalized) || "PLANNED".equals(normalized) || "DISABLED".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported route status");
    }

    private String normalizeScheduleStatus(String value) {
        String normalized = normalize(value);
        if (TransportConstants.SCHEDULE_STATUS_PLANNED.equals(normalized)
                || TransportConstants.SCHEDULE_STATUS_ACTIVE.equals(normalized)
                || TransportConstants.SCHEDULE_STATUS_SUSPENDED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported schedule status");
    }

    private String normalizeTaskStatus(String value) {
        String normalized = normalize(value);
        if (TransportConstants.TASK_STATUS_CREATED.equals(normalized)
                || TransportConstants.TASK_STATUS_ASSIGNED.equals(normalized)
                || TransportConstants.TASK_STATUS_DEPARTED.equals(normalized)
                || TransportConstants.TASK_STATUS_IN_TRANSIT.equals(normalized)
                || TransportConstants.TASK_STATUS_ARRIVED.equals(normalized)
                || TransportConstants.TASK_STATUS_COMPLETED.equals(normalized)
                || TransportConstants.TASK_STATUS_CANCELLED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "unsupported task status");
    }

    private long parseFacilityRef(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "facility reference is required");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "facility reference must be numeric");
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "value cannot be blank");
        }
        return value.trim().toUpperCase();
    }
}
