package com.liana.post.transport.service.impl;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.transport.constant.TransportConstants;
import com.liana.post.transport.client.DispatchClient;
import com.liana.post.transport.client.FacilityRouteClient;
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
import com.liana.post.transport.model.entity.TransportTaskBagEntity;
import com.liana.post.transport.repository.TransportRepository;
import com.liana.post.transport.service.TransportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class TransportServiceImpl implements TransportService {
    private static final Logger log = LoggerFactory.getLogger(TransportServiceImpl.class);

    private final TransportRepository transportRepository;
    private final com.liana.post.transport.service.ManifestAssignmentService assignments;
    private final com.liana.post.transport.service.ManifestAssignmentPublisher publisher;
    private final ObjectProvider<DispatchClient> dispatchClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;
    private final ObjectProvider<FacilityRouteClient> facilityRouteClientProvider;

    public TransportServiceImpl(TransportRepository transportRepository, ObjectProvider<DispatchClient> dispatchClientProvider, ObjectProvider<TrackingClient> trackingClientProvider, ObjectProvider<FacilityRouteClient> facilityRouteClientProvider, com.liana.post.transport.service.ManifestAssignmentService assignments, com.liana.post.transport.service.ManifestAssignmentPublisher publisher) {
        this.assignments = assignments;
        this.publisher = publisher;
        this.transportRepository = transportRepository;
        this.dispatchClientProvider = dispatchClientProvider;
        this.trackingClientProvider = trackingClientProvider;
        this.facilityRouteClientProvider = facilityRouteClientProvider;
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
        entity.setFacilityRouteCode(validateFacilityRoute(request));
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
        entity.setFacilityRouteCode(validateFacilityRoute(request));
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
        validateScheduleAssignment(request, null);
        entity.setDepartureTime(request.getDepartureTime());
        entity.setArrivalTime(request.getArrivalTime());
        entity.setWeekday(request.getWeekday().trim());
        entity.setStatus(normalizeScheduleStatus(request.getStatus()));
        return transportRepository.saveSchedule(entity);
    }

    @Override
    @Transactional
    public TransportScheduleEntity updateSchedule(String scheduleCode, TransportScheduleRequest request) {
        TransportScheduleEntity entity = transportRepository.findScheduleByCode(scheduleCode)
                .orElseThrow(() -> new BusinessException(404, "transport schedule not found: " + scheduleCode));
        entity = transportRepository.lockSchedule(entity.getId());
        validateSchedule(request);
        if (!transportRepository.findTasksByScheduleId(entity.getId()).isEmpty()
                && (!java.util.Objects.equals(entity.getAssetId(), request.getAssetId())
                || !java.util.Objects.equals(entity.getRouteId(), request.getRouteId())
                || !java.util.Objects.equals(entity.getDepartureTime(), request.getDepartureTime())
                || !java.util.Objects.equals(entity.getArrivalTime(), request.getArrivalTime()))) {
            throw new BusinessException(409, "班次已有任务或取消待确认分配，请先取消并等待释放，不能覆盖原运力和时刻");
        }
        validateScheduleAssignment(request, entity.getId());
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
    public TransportTaskEntity createTask(TransportTaskRequest request) {
        return assignments.create(request);
    }

    @Override
    public TransportTaskEntity createTaskFromDispatchBag(Long dispatchBagId) {
        var request = new TransportTaskRequest();
        request.setDispatchBagId(dispatchBagId);
        return assignments.create(request);
    }

    @Override
    public TransportTaskEntity updateTaskStatus(String taskCode, TransportTaskStatusRequest request) {
        var task = assignments.status(taskCode, request);
        if (!List.of("CANCELLING", "CANCELLED").contains(task.getStatus())) {
            for (var bag : assignments.bagIds(task.getId())) recordTrackingEvent(bag, taskCode, task.getStatus(), "task status updated");
        }
        return task;
    }

    @Override
    public TransportTaskEntity assignTask(String taskCode, Long scheduleId) {
        return assignments.assign(taskCode, scheduleId);
    }

    @Override
    public int retryUnlinkedTaskBags() {
        return publisher.publishDue();
    }

    @Override
    public TransportTaskEntity getTask(String taskCode) {
        return withBags(transportRepository.findTaskByCode(taskCode)
                .orElseThrow(() -> new BusinessException(404, "transport task not found: " + taskCode)));
    }

    @Override
    public PageResult<TransportTaskEntity> pageTasks(long page, long pageSize, String keyword, String status) {
        PageResult<TransportTaskEntity> result = transportRepository.pageTasks(page, pageSize, keyword, status);
        result.getList().forEach(this::withBags);
        return result;
    }

    private TransportTaskEntity withBags(TransportTaskEntity task) {
        task.setBagNos(transportRepository.findTaskBags(task.getId()).stream()
                .map(TransportTaskBagEntity::getBagNo).toList());
        return assignments.enrich(task);
    }

    @Override
    public DashboardSummaryResponse dashboardSummary() {
        PageResult<TransportAssetEntity> assets = pageAssets(1, 1000, null, null);
        PageResult<TransportRouteEntity> routes = pageRoutes(1, 1000, null, null, null);
        PageResult<TransportScheduleEntity> schedules = pageSchedules(1, 1000, null, null);
        PageResult<TransportTaskEntity> tasks = pageTasks(1, 1000, null, null);
        long availableAssets = assets.getList().stream().filter(item -> TransportConstants.STATUS_AVAILABLE.equals(item.getStatus())).count();
        long activeRoutes = routes.getList().stream().filter(item -> "ACTIVE".equals(item.getStatus())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("运输数据");
        response.setScope("全部机构");
        response.addMetric("运输资源", assets.getTotal(), "transport_asset 表", "info")
                .addMetric("可用资源", availableAssets, "AVAILABLE", "success")
                .addMetric("启用线路", activeRoutes, "ACTIVE", "neutral")
                .addMetric("运输任务", tasks.getTotal(), "transport_task 表", "warning");
        response.addBreakdown("资源状态", countBy(assets.getList().stream().map(TransportAssetEntity::getStatus).collect(Collectors.toList())));
        response.addBreakdown("任务状态", countBy(tasks.getList().stream().map(TransportTaskEntity::getStatus).collect(Collectors.toList())));
        response.addBreakdown("计划状态", countBy(schedules.getList().stream().map(TransportScheduleEntity::getStatus).collect(Collectors.toList())));
        return response;
    }

    private List<DashboardSummaryResponse.BreakdownItem> countBy(List<String> values) {
        return values.stream()
                .map(value -> value == null || value.isBlank() ? "UNKNOWN" : value)
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> DashboardSummaryResponse.item(entry.getKey(), entry.getValue(), entry.getKey()))
                .toList();
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

    private String validateFacilityRoute(TransportRouteRequest request) {
        if (!StringUtils.hasText(request.getFacilityRouteCode())) {
            throw new BusinessException(400, "facilityRouteCode is required");
        }
        FacilityRouteClient client = facilityRouteClientProvider.getIfAvailable();
        if (client == null) throw new BusinessException(503, "facility route service unavailable");
        Result<FacilityRouteClient.FacilityRouteView> result = client.getRoute(request.getFacilityRouteCode().trim());
        FacilityRouteClient.FacilityRouteView edge = result == null ? null : result.getData();
        if (result == null || !result.isSuccess() || edge == null || !Integer.valueOf(1).equals(edge.getStatus())
                || !request.getOriginFacilityCode().equalsIgnoreCase(edge.getOriginFacilityCode())
                || !request.getDestinationFacilityCode().equalsIgnoreCase(edge.getDestinationFacilityCode())
                || !request.getTransportType().equalsIgnoreCase(edge.getTransportMode())) {
            throw new BusinessException(400, "transport route must match an active facility route");
        }
        return edge.getRouteCode();
    }

    private void validateScheduleAssignment(TransportScheduleRequest request, Long excludedId) {
        TransportRouteEntity route = transportRepository.findRouteById(request.getRouteId())
                .orElseThrow(() -> new BusinessException(404, "transport route not found"));
        TransportAssetEntity asset = transportRepository.findAssetById(request.getAssetId())
                .orElseThrow(() -> new BusinessException(404, "transport asset not found"));
        if (!"ACTIVE".equals(route.getStatus()) || !assetMatchesRoute(asset, route)
                || !assetUsable(asset)) {
            throw new BusinessException(400, "route or asset is unavailable for this transport mode");
        }
        if (request.getDepartureTime() == null || request.getArrivalTime() == null
                || !request.getArrivalTime().isAfter(request.getDepartureTime())
                || !request.getDepartureTime().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new BusinessException(400, "schedule needs a future departure and later arrival");
        }
        if (!request.getDepartureTime().plusHours(12).getDayOfWeek().name().substring(0, 3).equalsIgnoreCase(request.getWeekday())) {
            throw new BusinessException(400, "weekday must match departure date at UTC+12");
        }
        if (!transportRepository.findOverlappingSchedules(asset.getId(), request.getDepartureTime(), request.getArrivalTime(), excludedId).isEmpty()) {
            throw new BusinessException(409, "asset already has an overlapping schedule");
        }
    }

    private boolean assetMatchesRoute(TransportAssetEntity asset, TransportRouteEntity route) {
        return switch (route.getTransportType()) {
            case "AIR" -> "AIRCRAFT".equals(asset.getType());
            case "SEA" -> "SHIP".equals(asset.getType());
            case "LAND" -> "TRUCK".equals(asset.getType());
            default -> false;
        };
    }

    private boolean assetUsable(TransportAssetEntity asset) {
        return "AVAILABLE".equals(asset.getStatus()) || "IN_SERVICE".equals(asset.getStatus());
    }

    private void validateSchedule(TransportScheduleRequest request) {
        if (request == null || !StringUtils.hasText(request.getScheduleCode())) {
            throw new BusinessException(400, "scheduleCode is required");
        }
    }

    private DispatchBagBriefResponse loadDispatchBag(Long id) {
        var client = dispatchClientProvider.getIfAvailable();
        if (client == null) throw new BusinessException(503, "dispatch client unavailable");
        var result = client.getBagById(id);
        if (result == null || !result.isSuccess() || result.getData() == null) throw new BusinessException(502, "bag lookup failed");
        return result.getData();
    }

    private void recordTrackingEvent(Long dispatchBagId, String taskCode, String status, String note) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { recordTrackingEventNow(dispatchBagId, taskCode, status, note); }
            });
            return;
        }
        recordTrackingEventNow(dispatchBagId, taskCode, status, note);
    }

    private void recordTrackingEventNow(Long dispatchBagId, String taskCode, String status, String note) {
        TrackingClient client = trackingClientProvider.getIfAvailable();
        if (client == null || dispatchBagId == null) {
            return;
        }
        try {
            DispatchBagBriefResponse bag = loadDispatchBag(dispatchBagId);
            for (String waybillNo : bag.getMailNoList() == null ? List.<String>of() : bag.getMailNoList()) {
                TrackingEventCreateRequest request = new TrackingEventCreateRequest();
                request.setWaybillNo(waybillNo);
                request.setEventType(mapStatusToEventType(status));
                request.setSourceService("TRANSPORT");
                request.setFacilityCode(TransportConstants.TASK_STATUS_ARRIVED.equals(status)
                        || TransportConstants.TASK_STATUS_COMPLETED.equals(status)
                        ? bag.getDestinationFacilityCode() : bag.getOriginFacilityCode());
                request.setFacilityName(null);
                request.setPayload("{\"taskCode\":\"" + taskCode + "\",\"dispatchBagId\":" + dispatchBagId + ",\"note\":\"" + note + "\"}");
                client.recordEvent(request);
            }
        } catch (Exception ex) {
            log.warn("failed to record tracking event for transport task {}", taskCode, ex);
        }
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

    private String parseFacilityRef(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "facility reference is required");
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "value cannot be blank");
        }
        return value.trim().toUpperCase();
    }
}
