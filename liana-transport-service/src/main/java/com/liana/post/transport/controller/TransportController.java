package com.liana.post.transport.controller;

import com.liana.post.common.model.Result;
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
import com.liana.post.transport.service.TransportService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transport")
public class TransportController {
    private final TransportService transportService;

    public TransportController(TransportService transportService) {
        this.transportService = transportService;
    }

    @PostConstruct
    public void init() {
        transportService.initDefaults();
    }

    @PostMapping("/assets")
    public Result<TransportAssetEntity> createAsset(@Valid @RequestBody TransportAssetRequest request) {
        return Result.ok(transportService.createAsset(request));
    }

    @PutMapping("/assets/{code}")
    public Result<TransportAssetEntity> updateAsset(@PathVariable String code, @Valid @RequestBody TransportAssetRequest request) {
        return Result.ok(transportService.updateAsset(code, request));
    }

    @GetMapping("/assets/{code}")
    public Result<TransportAssetEntity> getAsset(@PathVariable String code) {
        return Result.ok(transportService.getAsset(code));
    }

    @GetMapping("/assets")
    public Result<PageResult<TransportAssetEntity>> pageAssets(@RequestParam(name = "page", defaultValue = "1") long page,
                                                               @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
                                                               @RequestParam(name = "keyword", required = false) String keyword,
                                                               @RequestParam(name = "status", required = false) String status) {
        return Result.ok(transportService.pageAssets(page, pageSize, keyword, status));
    }

    @PostMapping("/routes")
    public Result<TransportRouteEntity> createRoute(@Valid @RequestBody TransportRouteRequest request) {
        return Result.ok(transportService.createRoute(request));
    }

    @PutMapping("/routes/{routeCode}")
    public Result<TransportRouteEntity> updateRoute(@PathVariable String routeCode, @Valid @RequestBody TransportRouteRequest request) {
        return Result.ok(transportService.updateRoute(routeCode, request));
    }

    @GetMapping("/routes/{routeCode}")
    public Result<TransportRouteEntity> getRoute(@PathVariable String routeCode) {
        return Result.ok(transportService.getRoute(routeCode));
    }

    @GetMapping("/routes")
    public Result<PageResult<TransportRouteEntity>> pageRoutes(@RequestParam(name = "page", defaultValue = "1") long page,
                                                               @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
                                                               @RequestParam(name = "keyword", required = false) String keyword,
                                                               @RequestParam(name = "status", required = false) String status,
                                                               @RequestParam(name = "transportType", required = false) String transportType) {
        return Result.ok(transportService.pageRoutes(page, pageSize, keyword, status, transportType));
    }

    @PostMapping("/schedules")
    public Result<TransportScheduleEntity> createSchedule(@Valid @RequestBody TransportScheduleRequest request) {
        return Result.ok(transportService.createSchedule(request));
    }

    @PutMapping("/schedules/{scheduleCode}")
    public Result<TransportScheduleEntity> updateSchedule(@PathVariable String scheduleCode, @Valid @RequestBody TransportScheduleRequest request) {
        return Result.ok(transportService.updateSchedule(scheduleCode, request));
    }

    @GetMapping("/schedules/{scheduleCode}")
    public Result<TransportScheduleEntity> getSchedule(@PathVariable String scheduleCode) {
        return Result.ok(transportService.getSchedule(scheduleCode));
    }

    @GetMapping("/schedules")
    public Result<PageResult<TransportScheduleEntity>> pageSchedules(@RequestParam(name = "page", defaultValue = "1") long page,
                                                                     @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
                                                                     @RequestParam(name = "keyword", required = false) String keyword,
                                                                     @RequestParam(name = "status", required = false) String status) {
        return Result.ok(transportService.pageSchedules(page, pageSize, keyword, status));
    }

    @PostMapping("/tasks")
    public Result<TransportTaskEntity> createTask(@Valid @RequestBody TransportTaskRequest request) {
        return Result.ok(transportService.createTask(request));
    }

    @PostMapping("/tasks/from-dispatch/{dispatchBagId}")
    public Result<TransportTaskEntity> createTaskFromDispatchBag(@PathVariable("dispatchBagId") Long dispatchBagId) {
        return Result.ok(transportService.createTaskFromDispatchBag(dispatchBagId));
    }

    @PostMapping("/tasks/{taskCode}/status")
    public Result<TransportTaskEntity> updateTaskStatus(@PathVariable String taskCode, @Valid @RequestBody TransportTaskStatusRequest request) {
        return Result.ok(transportService.updateTaskStatus(taskCode, request));
    }

    @GetMapping("/tasks/{taskCode}")
    public Result<TransportTaskEntity> getTask(@PathVariable String taskCode) {
        return Result.ok(transportService.getTask(taskCode));
    }

    @GetMapping("/tasks")
    public Result<PageResult<TransportTaskEntity>> pageTasks(@RequestParam(name = "page", defaultValue = "1") long page,
                                                             @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
                                                             @RequestParam(name = "keyword", required = false) String keyword,
                                                             @RequestParam(name = "status", required = false) String status) {
        return Result.ok(transportService.pageTasks(page, pageSize, keyword, status));
    }
}
