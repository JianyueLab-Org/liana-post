package com.liana.post.sorting.controller;

import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.model.Result;
import com.liana.post.sorting.model.dto.*;
import com.liana.post.sorting.service.SortingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sorting")
public class SortingController {
    private final SortingService sortingService;

    public SortingController(SortingService sortingService) {
        this.sortingService = sortingService;
    }

    @PostMapping("/manifest/sync")
    public Result<Boolean> syncManifest(@Valid @RequestBody ManifestDTO request) {
        sortingService.syncManifest(request);
        return Result.ok(true);
    }

    @PostMapping("/receive")
    public Result<SortingPackageResponse> receive(@Valid @RequestBody SortingReceiveRequest request) {
        return Result.ok(sortingService.receive(request));
    }

    @PostMapping("/unpack-item")
    public Result<List<SortingLineResponse>> unpackItem(@Valid @RequestBody SortingUnpackItemRequest request) {
        return Result.ok(sortingService.unpackItem(request));
    }

    @PostMapping("/route-calculate")
    public Result<SortingRouteResponse> routeCalculate(@Valid @RequestBody SortingRouteCalculateRequest request) {
        return Result.ok(sortingService.routeCalculate(request));
    }

    @PostMapping("/route-calculate/scan")
    public Result<SortingSecurityAuditResponse> routeCalculateByScan(@Valid @RequestBody SortingRouteScanRequest request) {
        return Result.ok(sortingService.routeCalculate(request));
    }

    @PostMapping("/re-bag")
    public Result<SortingPackageResponse> reBag(@Valid @RequestBody SortingRebagRequest request) {
        return Result.ok(sortingService.reBag(request));
    }

    @GetMapping("/slots")
    public Result<List<SortingSlotSummaryResponse>> listSlots(@RequestParam(name = "stationCode", required = false) String stationCode) {
        return Result.ok(sortingService.listSlotSummaries(stationCode));
    }

    @PostMapping("/slots/seal")
    public Result<SortingPackageResponse> sealBagBySlot(@RequestBody java.util.Map<String, String> payload) {
        return Result.ok(sortingService.sealBagBySlot(payload.get("slotCode"), payload.get("stationCode"), payload.get("operatorId")));
    }

    @GetMapping("/country-slots")
    public Result<List<CountrySlotSummaryResponse>> listCountrySlots(@RequestParam(name = "stationCode", required = false) String stationCode) {
        return Result.ok(sortingService.listCountrySlotSummaries(stationCode));
    }

    @PostMapping("/country-slots/seal")
    public Result<List<SortingPackageResponse>> sealCountrySlot(@Valid @RequestBody CountrySlotBulkRequest request) {
        return Result.ok(sortingService.sealCountrySlot(request));
    }

    @GetMapping("/packages")
    public Result<List<SortingPackageResponse>> listPackages(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return Result.ok(sortingService.listPackages(authorization));
    }

    @GetMapping("/manifests")
    public Result<List<SortingManifestResponse>> listManifests() {
        return Result.ok(sortingService.listManifests());
    }

    @GetMapping("/manifests/{manifestNo}")
    public Result<SortingManifestDetailResponse> getManifestDetail(@org.springframework.web.bind.annotation.PathVariable("manifestNo") String manifestNo) {
        return Result.ok(sortingService.getManifestDetail(manifestNo));
    }

    @GetMapping("/lines")
    public Result<List<SortingLineResponse>> listLines(@RequestParam(name = "packageNo", required = false) String packageNo) {
        return Result.ok(sortingService.listLines(packageNo));
    }

    @GetMapping("/discrepancies")
    public Result<List<SortingDiscrepancyResponse>> listDiscrepancies(@RequestParam(name = "packageNo", required = false) String packageNo) {
        return Result.ok(sortingService.listDiscrepancies(packageNo));
    }
}
