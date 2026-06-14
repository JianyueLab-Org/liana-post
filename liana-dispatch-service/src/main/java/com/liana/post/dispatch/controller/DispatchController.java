
package com.liana.post.dispatch.controller;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.sorting.ManifestArrivedRequest;
import com.liana.post.common.model.Result;
import com.liana.post.dispatch.model.dto.DispatchBagCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBagResponse;
import com.liana.post.dispatch.model.dto.DispatchBatchApproveRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchResponse;
import com.liana.post.dispatch.model.dto.DispatchMailSyncRequest;
import com.liana.post.dispatch.model.dto.HandoffCreateRequest;
import com.liana.post.dispatch.model.dto.HandoffRecordResponse;
import com.liana.post.dispatch.model.dto.RouteRuleCreateRequest;
import com.liana.post.dispatch.model.entity.RouteRuleEntity;
import com.liana.post.dispatch.service.DispatchService;
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
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void init() {
        dispatchService.initDefaults();
    }

    @PostMapping("/route-rules")
    public Result<RouteRuleEntity> createRouteRule(@Valid @RequestBody RouteRuleCreateRequest request) {
        return Result.ok(dispatchService.createRouteRule(request));
    }

    @PostMapping("/bags")
    public Result<DispatchBagResponse> createBag(@Valid @RequestBody DispatchBagCreateRequest request) {
        return Result.ok(dispatchService.createDispatchBag(request));
    }

    @PostMapping("/bags/sync-mail")
    public Result<DispatchBagBriefResponse> syncMailBag(@Valid @RequestBody DispatchMailSyncRequest request) {
        DispatchBagResponse bag = dispatchService.syncMailBag(request);
        return Result.ok(toBrief(bag));
    }

    @GetMapping("/bags/by-id/{id}")
    public Result<DispatchBagResponse> getBagById(@PathVariable("id") Long id) {
        return Result.ok(dispatchService.getDispatchBagById(id));
    }

    @PostMapping("/bags/by-id/{id}/transport-task")
    public Result<DispatchBagResponse> linkTransportTask(@PathVariable("id") Long id, @Valid @RequestBody DispatchTransportTaskLinkRequest request) {
        return Result.ok(dispatchService.linkTransportTask(id, request));
    }

    @PostMapping("/batches")
    public Result<DispatchBatchResponse> createBatch(@Valid @RequestBody DispatchBatchCreateRequest request) {
        return Result.ok(dispatchService.createDispatchBatch(request));
    }

    @PostMapping("/batches/{batchNo}/approve")
    public Result<DispatchBatchResponse> approveBatch(@PathVariable("batchNo") String batchNo, @Valid @RequestBody DispatchBatchApproveRequest request) {
        return Result.ok(dispatchService.approveDispatchBatch(batchNo, request));
    }

    @PostMapping("/handoffs")
    public Result<HandoffRecordResponse> createHandoff(@Valid @RequestBody HandoffCreateRequest request) {
        return Result.ok(dispatchService.createHandoffRecord(request));
    }

    @PostMapping("/manifests/arrived")
    public Result<Boolean> arrived(@Valid @RequestBody ManifestArrivedRequest request) {
        return Result.ok(dispatchService.markManifestArrived(request));
    }

    @GetMapping("/bags")
    public Result<List<DispatchBagResponse>> listBags() {
        return Result.ok(dispatchService.listDispatchBags());
    }

    @GetMapping("/batches")
    public Result<List<DispatchBatchResponse>> listBatches() {
        return Result.ok(dispatchService.listDispatchBatches());
    }

    @GetMapping("/handoffs")
    public Result<List<HandoffRecordResponse>> listHandoffs() {
        return Result.ok(dispatchService.listHandoffRecords());
    }

    private DispatchBagBriefResponse toBrief(DispatchBagResponse bag) {
        DispatchBagBriefResponse brief = new DispatchBagBriefResponse();
        brief.setDispatchBagId(bag.getDispatchBagId());
        brief.setBagNo(bag.getBagNo());
        brief.setOriginFacilityCode(bag.getOriginFacilityCode());
        brief.setDestinationFacilityCode(bag.getDestinationFacilityCode());
        brief.setRouteCode(bag.getRouteCode());
        brief.setStatus(bag.getStatus());
        brief.setMailTypeCode(bag.getMailTypeCode());
        brief.setMailCount(bag.getMailCount());
        brief.setTotalWeightGrams(bag.getTotalWeightGrams());
        return brief;
    }
}
