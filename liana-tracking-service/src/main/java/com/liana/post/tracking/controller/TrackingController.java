package com.liana.post.tracking.controller;

import com.liana.post.common.model.Result;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.tracking.model.dto.TrackingEventResponse;
import com.liana.post.tracking.model.dto.TrackingQueryRequest;
import com.liana.post.tracking.service.TrackingService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/tracking", "/api/records"})
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostConstruct
    public void init() {
        trackingService.initDefaults();
    }

    @PostMapping("/events")
    public Result<TrackingEventResponse> recordEvent(@Valid @RequestBody TrackingEventCreateRequest request) {
        return Result.ok(trackingService.recordEvent(request));
    }

    @GetMapping("/events/{eventNo}")
    public Result<TrackingEventResponse> getByEventNo(@PathVariable("eventNo") String eventNo) {
        return Result.ok(trackingService.getByEventNo(eventNo));
    }

    @GetMapping("/events")
    public Result<List<TrackingEventResponse>> listEvents() {
        return Result.ok(trackingService.listEvents());
    }

    @GetMapping("/events/waybill/{waybillNo}")
    public Result<List<TrackingEventResponse>> listByWaybillNo(@PathVariable("waybillNo") String waybillNo) {
        return Result.ok(trackingService.listByWaybillNo(waybillNo));
    }

    @PostMapping("/events/search")
    public Result<List<TrackingEventResponse>> search(@RequestBody TrackingQueryRequest request) {
        return Result.ok(trackingService.search(request));
    }
}
