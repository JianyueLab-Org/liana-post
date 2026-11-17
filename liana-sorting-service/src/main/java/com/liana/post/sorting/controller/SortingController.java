package com.liana.post.sorting.controller;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.model.Result;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.model.dto.*;
import com.liana.post.sorting.service.SortingService;
import com.liana.post.sorting.service.ManifestPrealertService;
import com.liana.post.sorting.service.ManifestSealService;
import com.liana.post.sorting.service.ManifestCancelService;
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
    private final ManifestPrealertService manifestPrealertService;
    private final ManifestSealService manifestSealService;
    private final ManifestCancelService manifestCancelService;
    private final com.liana.post.sorting.service.ManifestReceivingService receiving;
    private final com.liana.post.sorting.service.UnpackingService unpacking;
    private final com.liana.post.sorting.service.DiscrepancyDispositionService discrepancyDisposition;

    public SortingController(SortingService sortingService, ManifestPrealertService manifestPrealertService, ManifestSealService manifestSealService,
                             ManifestCancelService manifestCancelService, com.liana.post.sorting.service.ManifestReceivingService receiving,
                             com.liana.post.sorting.service.UnpackingService unpacking,
                             com.liana.post.sorting.service.DiscrepancyDispositionService discrepancyDisposition) {
        this.sortingService = sortingService;
        this.manifestPrealertService = manifestPrealertService;
        this.manifestSealService = manifestSealService;
        this.manifestCancelService = manifestCancelService;
        this.receiving = receiving;
        this.unpacking = unpacking;
        this.discrepancyDisposition = discrepancyDisposition;
    }

    @GetMapping("/manifests/{manifestNo}/versions")
    public Result<List<ManifestPrealertService.Version>> manifestVersions(
            @org.springframework.web.bind.annotation.PathVariable("manifestNo") String manifestNo,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        authorizeManifestHistory(manifestNo, authorization);
        return Result.ok(manifestPrealertService.versions(manifestNo));
    }

    @GetMapping("/manifests/{manifestNo}/seal-receipt")
    public Result<ManifestSealService.Receipt> manifestSealReceipt(
            @org.springframework.web.bind.annotation.PathVariable("manifestNo") String manifestNo,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        authorizeManifestHistory(manifestNo, authorization);
        return Result.ok(manifestSealService.receipt(manifestNo));
    }

    @GetMapping("/manifests/{manifestNo}/bag-receipts")
    public Result<List<java.util.Map<String, Object>>> bagReceipts(
            @org.springframework.web.bind.annotation.PathVariable("manifestNo") String manifestNo,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        authorizeManifestHistory(manifestNo, authorization);
        return Result.ok(receiving.history(manifestNo));
    }

    private void authorizeManifestHistory(String manifestNo, String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        if (!JwtTokenUtil.validateToken(token)) throw new BusinessException(401, "valid login required");
        var actor = JwtTokenUtil.getUserContextFromToken(token);
        var manifest = sortingService.getManifestDetail(manifestNo);
        if (!"ADMIN".equals(actor.getRole()) && (actor.getFacilityCode() == null
                || (!actor.getFacilityCode().equalsIgnoreCase(manifest.getSourceOrgCode())
                && !actor.getFacilityCode().equalsIgnoreCase(manifest.getDestinationOrgCode())))) {
            throw new BusinessException(403, "manifest history belongs to its handoff facilities");
        }
    }

    @GetMapping("/manifests/{manifestNo}/cancel-receipt")
    public Result<ManifestCancelService.Receipt> manifestCancelReceipt(
            @org.springframework.web.bind.annotation.PathVariable("manifestNo") String manifestNo,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        authorizeManifestHistory(manifestNo, authorization);
        return Result.ok(manifestCancelService.receipt(manifestNo));
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

    @GetMapping("/unpack-requests/{requestNo}")
    public Result<com.liana.post.sorting.service.UnpackingService.Receipt> unpackReceipt(
            @org.springframework.web.bind.annotation.PathVariable("requestNo") String requestNo) {
        return Result.ok(unpacking.receipt(requestNo));
    }

    @PostMapping("/route-calculate")
    public Result<SortingRouteResponse> routeCalculate(@Valid @RequestBody SortingRouteCalculateRequest request) {
        return Result.ok(sortingService.routeCalculate(request));
    }

    @PostMapping("/route-calculate/scan")
    public Result<SortingSecurityAuditResponse> routeCalculateByScan(@Valid @RequestBody SortingRouteScanRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        if (request.getManualNextHopCode() != null && !request.getManualNextHopCode().isBlank()) {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
            if (!JwtTokenUtil.validateToken(token) || !"ADMIN".equalsIgnoreCase(JwtTokenUtil.getRoleFromToken(token))) {
                throw new BusinessException(403, "administrator required");
            }
        }
        return Result.ok(sortingService.routeCalculate(request));
    }

    @GetMapping("/route-calculate/pending")
    public Result<List<SortingPendingRouteItemResponse>> listPendingRouteItems(@RequestParam(name = "stationCode", required = false) String stationCode) {
        return Result.ok(sortingService.listPendingRouteItems(stationCode));
    }

    @PostMapping("/re-bag")
    public Result<SortingPackageResponse> reBag(@Valid @RequestBody SortingRebagRequest request) {
        return Result.ok(sortingService.reBag(request));
    }

    @GetMapping("/unpack-preview")
    public Result<SortingUnpackPreviewResponse> previewUnpackItems(
            @RequestParam("packageNo") String packageNo,
            @RequestParam(name = "manifestNo", required = false) String manifestNo) {
        return Result.ok(sortingService.previewUnpackItems(packageNo, manifestNo));
    }

    @GetMapping("/slots")
    public Result<List<SortingSlotSummaryResponse>> listSlots(@RequestParam(name = "stationCode", required = false) String stationCode) {
        return Result.ok(sortingService.listSlotSummaries(stationCode));
    }

    @PostMapping("/slots/seal")
    public Result<SortingPackageResponse> sealBagBySlot(@RequestBody java.util.Map<String, String> payload) {
        return Result.ok(sortingService.sealBagBySlot(payload.get("slotCode"), payload.get("stationCode"), payload.get("operatorId"), payload.get("idempotencyKey")));
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
    public Result<List<SortingManifestResponse>> listManifests(
            @RequestParam(name = "receiveCandidate", defaultValue = "true") boolean receiveCandidate,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        return Result.ok(sortingService.listManifests(receiveCandidate, authorization));
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

    @PostMapping("/discrepancies/{verificationNo}/disposition")
    public Result<com.liana.post.sorting.service.DiscrepancyDispositionService.Receipt> disposeDiscrepancy(
            @org.springframework.web.bind.annotation.PathVariable("verificationNo") String verificationNo,
            @RequestBody com.liana.post.sorting.service.DiscrepancyDispositionService.Request request) {
        return Result.ok(discrepancyDisposition.apply(verificationNo, request));
    }

    @GetMapping("/dashboard/summary")
    public Result<DashboardSummaryResponse> dashboardSummary(@RequestParam(name = "stationCode", required = false) String stationCode) {
        return Result.ok(sortingService.dashboardSummary(stationCode));
    }
}
