package com.liana.post.transport.service.impl;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.IdGeneratorUtil;
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
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class TransportServiceImpl implements TransportService {
    private static final Logger log = LoggerFactory.getLogger(TransportServiceImpl.class);

    private final TransportRepository transportRepository;
    private final ObjectProvider<DispatchClient> dispatchClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;
    private final ObjectProvider<FacilityRouteClient> facilityRouteClientProvider;

    public TransportServiceImpl(TransportRepository transportRepository, ObjectProvider<DispatchClient> dispatchClientProvider, ObjectProvider<TrackingClient> trackingClientProvider, ObjectProvider<FacilityRouteClient> facilityRouteClientProvider) {
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
    public TransportScheduleEntity updateSchedule(String scheduleCode, TransportScheduleRequest request) {
        TransportScheduleEntity entity = transportRepository.findScheduleByCode(scheduleCode)
                .orElseThrow(() -> new BusinessException(404, "transport schedule not found: " + scheduleCode));
        validateSchedule(request);
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
    @Transactional
    public TransportTaskEntity createTask(TransportTaskRequest request) {
        validateTask(request);
        DispatchBagBriefResponse bag = loadDispatchBag(request.getDispatchBagId());
        if (transportRepository.findTaskByDispatchBagId(request.getDispatchBagId()).isPresent()) {
            throw new BusinessException(409, "transport task already exists for dispatch bag");
        }
        if (request.getScheduleId() != null) {
            TransportTaskEntity existing = transportRepository.findActiveTaskByScheduleId(request.getScheduleId()).orElse(null);
            if (existing != null) {
                TransportScheduleEntity schedule = transportRepository.lockSchedule(request.getScheduleId());
                if (!canCarry(schedule, bag)) throw new BusinessException(409, "schedule cannot carry this bag");
                transportRepository.addTaskBag(taskBag(existing, bag));
                linkDispatchBag(bag.getDispatchBagId(), existing.getTaskCode());
                recordTrackingEvent(bag.getDispatchBagId(), existing.getTaskCode(), existing.getStatus(), "bag loaded");
                return existing;
            }
        }
        TransportTaskEntity entity = new TransportTaskEntity();
        entity.setTaskCode(StringUtils.hasText(request.getTaskCode()) ? request.getTaskCode().trim() : IdGeneratorUtil.generateBizNo("TT"));
        entity.setDispatchBagId(request.getDispatchBagId());
        entity.setStatus(TransportConstants.TASK_STATUS_CREATED);
        if (request.getScheduleId() != null) {
            assignSchedule(entity, bag, request.getScheduleId());
        } else if (request.getAssetId() != null || request.getRouteId() != null) {
            throw new BusinessException(400, "select a schedule to assign its route and asset");
        }
        entity = transportRepository.saveTask(entity);
        transportRepository.addTaskBag(taskBag(entity, bag));
        linkDispatchBag(entity.getDispatchBagId(), entity.getTaskCode());
        if (entity.getScheduleId() != null) recordTrackingEvent(entity.getDispatchBagId(), entity.getTaskCode(), entity.getStatus(), "task scheduled");
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
            return transportRepository.findTaskByDispatchBagId(dispatchBagId).orElseThrow();
        }
        TransportTaskEntity entity = new TransportTaskEntity();
        entity.setTaskCode(IdGeneratorUtil.generateBizNo("TT"));
        entity.setDispatchBagId(dispatchBagId);
        entity.setStatus(TransportConstants.TASK_STATUS_CREATED);
        TransportTaskEntity pendingTask = entity;
        if (StringUtils.hasText(bag.getFacilityRouteCode())) {
            transportRepository.findRoutesByFacilityRouteCode(bag.getFacilityRouteCode()).stream()
                    .flatMap(route -> transportRepository.findFutureSchedulesByRouteId(route.getId()).stream())
                    .sorted(Comparator.comparing(TransportScheduleEntity::getDepartureTime))
                    .filter(schedule -> canCarry(schedule, bag))
                    .findFirst().ifPresent(schedule -> assignSchedule(pendingTask, bag, schedule.getId()));
        }
        if (entity.getScheduleId() != null) {
            TransportTaskEntity existing = transportRepository.findActiveTaskByScheduleId(entity.getScheduleId()).orElse(null);
            if (existing != null) {
                transportRepository.addTaskBag(taskBag(existing, bag));
                linkDispatchBag(dispatchBagId, existing.getTaskCode());
                recordTrackingEvent(dispatchBagId, existing.getTaskCode(), existing.getStatus(), "bag loaded");
                return existing;
            }
        }
        entity = transportRepository.saveTask(entity);
        transportRepository.addTaskBag(taskBag(entity, bag));
        linkDispatchBag(dispatchBagId, entity.getTaskCode());
        if (entity.getScheduleId() != null) recordTrackingEvent(dispatchBagId, entity.getTaskCode(), entity.getStatus(), "task scheduled from dispatch bag");
        return entity;
    }

    @Override
    @Transactional
    public TransportTaskEntity updateTaskStatus(String taskCode, TransportTaskStatusRequest request) {
        TransportTaskEntity entity = transportRepository.findTaskByCode(taskCode)
                .orElseThrow(() -> new BusinessException(404, "transport task not found: " + taskCode));
        String newStatus = normalizeTaskStatus(request.getStatus());
        if (!canTransition(entity.getStatus(), newStatus)) {
            throw new BusinessException(409, "invalid transport task status transition");
        }
        if (!TransportConstants.TASK_STATUS_CANCELLED.equals(newStatus)
                && !TransportConstants.TASK_STATUS_CREATED.equals(newStatus) && entity.getScheduleId() == null) {
            throw new BusinessException(409, "task has no assigned schedule");
        }
        if (TransportConstants.TASK_STATUS_DEPARTED.equals(newStatus)) {
            TransportScheduleEntity schedule = transportRepository.findScheduleById(entity.getScheduleId())
                    .orElseThrow(() -> new BusinessException(404, "transport schedule not found"));
            if ("SUSPENDED".equals(schedule.getStatus())) throw new BusinessException(409, "schedule is suspended");
            for (TransportTaskBagEntity loaded : transportRepository.findTaskBags(entity.getId())) {
                DispatchBagBriefResponse bag = loadDispatchBag(loaded.getDispatchBagId());
                if (!"DISPATCHED".equals(bag.getStatus())) {
                    throw new BusinessException(409, "all loaded bags must be handed off before departure");
                }
            }
        }
        entity.setStatus(newStatus);
        entity = transportRepository.updateTask(entity);
        if (!TransportConstants.TASK_STATUS_CANCELLED.equals(newStatus)) {
            for (TransportTaskBagEntity loaded : transportRepository.findTaskBags(entity.getId())) {
                recordTrackingEvent(loaded.getDispatchBagId(), entity.getTaskCode(), newStatus, "task status updated");
            }
        }
        return entity;
    }

    @Override
    @Transactional
    public TransportTaskEntity assignTask(String taskCode, Long scheduleId) {
        TransportTaskEntity task = transportRepository.findTaskByCode(taskCode)
                .orElseThrow(() -> new BusinessException(404, "transport task not found: " + taskCode));
        if (!TransportConstants.TASK_STATUS_CREATED.equals(task.getStatus())) {
            throw new BusinessException(409, "only pending tasks can be assigned");
        }
        assignSchedule(task, loadDispatchBag(task.getDispatchBagId()), scheduleId);
        task = transportRepository.updateTask(task);
        recordTrackingEvent(task.getDispatchBagId(), task.getTaskCode(), task.getStatus(), "schedule assigned");
        return task;
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    public int retryUnlinkedTaskBags() {
        int linked = 0;
        for (TransportTaskBagEntity bag : transportRepository.findUnlinkedTaskBags()) {
            TransportTaskEntity task = transportRepository.findTaskById(bag.getTaskId()).orElse(null);
            if (task == null) continue;
            try {
                linkDispatchBagNow(bag.getDispatchBagId(), task.getTaskCode());
                transportRepository.markTaskBagLinked(bag.getId());
                linked++;
            } catch (Exception ex) {
                log.warn("failed to retry dispatch link for bag {}", bag.getDispatchBagId(), ex);
            }
        }
        return linked;
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
        return task;
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

    @Override
    public void initDefaults() {
        transportRepository.seedDefaults();
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
                || !request.getDepartureTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "schedule needs a future departure and later arrival");
        }
        if (!request.getDepartureTime().getDayOfWeek().name().substring(0, 3).equalsIgnoreCase(request.getWeekday())) {
            throw new BusinessException(400, "weekday must match departure date");
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
        Result<DispatchBagBriefResponse> response = client.getBagById(dispatchBagId);
        if (response == null || response.getCode() == null || response.getCode() != 200) {
            String message = response == null || !StringUtils.hasText(response.getMessage())
                    ? "dispatch bag lookup failed: " + dispatchBagId
                    : response.getMessage();
            throw new BusinessException(502, message);
        }
        DispatchBagBriefResponse bag = response.getData();
        if (bag == null) {
            throw new BusinessException(404, "dispatch bag not found: " + dispatchBagId);
        }
        return bag;
    }

    private boolean canCarry(TransportScheduleEntity schedule, DispatchBagBriefResponse bag) {
        TransportRouteEntity route = transportRepository.findRouteById(schedule.getRouteId()).orElse(null);
        TransportAssetEntity asset = transportRepository.findAssetById(schedule.getAssetId()).orElse(null);
        if (route == null || asset == null || !assetUsable(asset) || !assetMatchesRoute(asset, route)
                || !"ACTIVE".equals(route.getStatus()) || "SUSPENDED".equals(schedule.getStatus())
                || !schedule.getDepartureTime().isAfter(LocalDateTime.now())
                || route.getFacilityRouteCode() == null
                || !route.getFacilityRouteCode().equalsIgnoreCase(bag.getFacilityRouteCode())
                || !route.getOriginFacilityId().equalsIgnoreCase(bag.getOriginFacilityCode())
                || !route.getDestinationFacilityId().equalsIgnoreCase(bag.getDestinationFacilityCode())) return false;
        BigDecimal used = BigDecimal.ZERO;
        for (TransportTaskEntity task : transportRepository.findTasksByScheduleId(schedule.getId())) {
            for (TransportTaskBagEntity loaded : transportRepository.findTaskBags(task.getId())) {
                used = used.add(BigDecimal.valueOf(loaded.getWeightGrams() == null ? 0 : loaded.getWeightGrams()));
            }
        }
        BigDecimal weight = BigDecimal.valueOf(bag.getTotalWeightGrams() == null ? 0 : bag.getTotalWeightGrams());
        return asset.getCapacity() != null && used.add(weight).compareTo(asset.getCapacity().multiply(BigDecimal.valueOf(1000))) <= 0;
    }

    private void assignSchedule(TransportTaskEntity task, DispatchBagBriefResponse bag, Long scheduleId) {
        TransportScheduleEntity schedule = transportRepository.lockSchedule(scheduleId);
        if (!StringUtils.hasText(bag.getFacilityRouteCode()) || !canCarry(schedule, bag)) {
            throw new BusinessException(409, "schedule does not match the bag route or has no capacity");
        }
        task.setScheduleId(schedule.getId());
        task.setRouteId(schedule.getRouteId());
        task.setAssetId(schedule.getAssetId());
        task.setStatus(TransportConstants.TASK_STATUS_ASSIGNED);
    }

    private TransportTaskBagEntity taskBag(TransportTaskEntity task, DispatchBagBriefResponse bag) {
        TransportTaskBagEntity link = new TransportTaskBagEntity();
        link.setTaskId(task.getId());
        link.setDispatchBagId(bag.getDispatchBagId());
        link.setBagNo(bag.getBagNo());
        link.setWeightGrams(bag.getTotalWeightGrams() == null ? 0 : bag.getTotalWeightGrams());
        link.setCreatedAt(LocalDateTime.now());
        return link;
    }

    private boolean canTransition(String oldStatus, String newStatus) {
        if (oldStatus.equals(newStatus)) return true;
        return switch (oldStatus) {
            case "CREATED" -> newStatus.equals("ASSIGNED") || newStatus.equals("CANCELLED");
            case "ASSIGNED" -> newStatus.equals("DEPARTED") || newStatus.equals("CANCELLED");
            case "DEPARTED" -> newStatus.equals("IN_TRANSIT") || newStatus.equals("ARRIVED");
            case "IN_TRANSIT" -> newStatus.equals("ARRIVED");
            case "ARRIVED" -> newStatus.equals("COMPLETED");
            default -> false;
        };
    }

    private void linkDispatchBag(Long dispatchBagId, String taskCode) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        linkDispatchBagNow(dispatchBagId, taskCode);
                    } catch (Exception ex) {
                        log.warn("dispatch link pending retry for bag {} and task {}", dispatchBagId, taskCode, ex);
                    }
                }
            });
            return;
        }
        linkDispatchBagNow(dispatchBagId, taskCode);
    }

    private void linkDispatchBagNow(Long dispatchBagId, String taskCode) {
        DispatchClient client = dispatchClientProvider.getIfAvailable();
        if (client == null) throw new BusinessException(503, "dispatch client unavailable");
        DispatchTransportTaskLinkRequest request = new DispatchTransportTaskLinkRequest();
        request.setTransportTaskCode(taskCode);
        Result<DispatchBagBriefResponse> linked = client.linkTransportTask(dispatchBagId, request);
        if (linked == null || !linked.isSuccess()) throw new BusinessException(502, "failed to link dispatch bag to transport task");
        transportRepository.findTaskBagByDispatchBagId(dispatchBagId).ifPresent(link -> transportRepository.markTaskBagLinked(link.getId()));
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
