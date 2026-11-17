package com.liana.post.sorting.service.impl;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.ManifestSealCommand;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.sorting.client.TrackingClient;
import com.liana.post.sorting.constant.SortingConstants;
import com.liana.post.sorting.model.dto.*;
import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.model.entity.PackageItemLineEntity;
import com.liana.post.sorting.model.entity.SortingManifestEntity;
import com.liana.post.sorting.model.entity.SortingManifestItemEntity;
import com.liana.post.sorting.model.entity.SortingManifestPackageEntity;
import com.liana.post.sorting.model.entity.SortingTotalPackageEntity;
import com.liana.post.sorting.repository.SortingRepository;
import com.liana.post.sorting.strategy.RoutingStrategy;
import com.liana.post.sorting.service.SortingService;
import com.liana.post.sorting.service.ManifestPrealertService;
import com.liana.post.sorting.service.ManifestSealService;
import com.liana.post.sorting.service.XraySecurityAuditSimulator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SortingServiceImpl implements SortingService {

    private final SortingRepository sortingRepository;
    private final ManifestPrealertService manifestPrealertService;
    private final ManifestSealService manifestSealService;
    private final com.liana.post.sorting.service.ManifestReceivingService receiving;
    private final com.liana.post.sorting.service.UnpackingService unpacking;
    private final com.liana.post.sorting.service.RepackingService repacking;
    private final RoutingStrategy routingStrategy;
    private final ObjectProvider<OmsClient> omsClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;
    private final ObjectProvider<DispatchLifecycleClient> dispatchLifecycleClientProvider;
    private final XraySecurityAuditSimulator xraySecurityAuditSimulator;

    public SortingServiceImpl(SortingRepository sortingRepository,
                              RoutingStrategy routingStrategy,
                              ObjectProvider<OmsClient> omsClientProvider,
                              ObjectProvider<TrackingClient> trackingClientProvider,
                              ObjectProvider<DispatchLifecycleClient> dispatchLifecycleClientProvider,
                              XraySecurityAuditSimulator xraySecurityAuditSimulator,
                              ManifestPrealertService manifestPrealertService,
                              ManifestSealService manifestSealService,
                              com.liana.post.sorting.service.ManifestReceivingService receiving,
                              com.liana.post.sorting.service.UnpackingService unpacking,
                              com.liana.post.sorting.service.RepackingService repacking) {
        this.sortingRepository = sortingRepository;
        this.manifestPrealertService = manifestPrealertService;
        this.manifestSealService = manifestSealService;
        this.receiving = receiving;
        this.unpacking = unpacking;
        this.repacking = repacking;
        this.routingStrategy = routingStrategy;
        this.omsClientProvider = omsClientProvider;
        this.trackingClientProvider = trackingClientProvider;
        this.dispatchLifecycleClientProvider = dispatchLifecycleClientProvider;
        this.xraySecurityAuditSimulator = xraySecurityAuditSimulator;
    }

    @Override
    @Transactional
    public void syncManifest(ManifestDTO request) {
        manifestPrealertService.accept(request);
    }

    @Override
    public SortingPackageResponse receive(SortingReceiveRequest request) {
        return receiving.receive(request);
    }

    @Override
    public List<SortingLineResponse> unpackItem(SortingUnpackItemRequest request) {
        return unpacking.unpack(request);
    }

    @Override
    @Transactional
    public SortingSecurityAuditResponse routeCalculate(SortingRouteScanRequest request) {
        String itemNo = normalizeBlank(request.getItemNo());
        unpacking.requireMailReady(itemNo, request.getStationCode());
        OmsClient omsClient = omsClientProvider.getIfAvailable();
        com.liana.post.common.model.Result<com.liana.post.oms.model.dto.MailResponse> mailResult = omsClient == null ? null : omsClient.getMail(itemNo);
        com.liana.post.oms.model.dto.MailResponse mail = mailResult == null ? null : mailResult.getData();
        String currentFacilityCode = request.getStationCode();
        RouteResult routeResult = StringUtils.hasText(request.getManualNextHopCode())
                ? routingStrategy.doManualRoute(itemNo, currentFacilityCode, request.getManualNextHopCode())
                : routingStrategy.doRoute(itemNo, currentFacilityCode);
        XraySecurityAuditSimulator.XrayAuditSample xray = xraySecurityAuditSimulator.sample();
        List<String> dangerLabels = resolveDangerLabels(mail, xray.getLabels());
        boolean xrayDanger = !dangerLabels.isEmpty();
        boolean strategyDanger = SortingConstants.SECURITY_STATUS_DANGER.equalsIgnoreCase(routeResult.getSecurityStatus());
        boolean danger = strategyDanger || xrayDanger;
        String routeCode = danger && xrayDanger ? "RETURN_ORIGIN" : routeResult.getSlotCode();
        String nextHop = danger && xrayDanger ? resolveOriginFacilityCode(mail) : routeResult.getNextHopCode();
        String targetNode = danger && xrayDanger ? resolveOriginFacilityCode(mail) : routeResult.getTargetNodeName();
        boolean manualReview = !danger && "MANUAL_REVIEW".equalsIgnoreCase(routeCode);
        String message = danger && xrayDanger
                ? "Security blocked: " + String.join(", ", dangerLabels)
                : routeResult.getMessage();

        if (!manualReview) {
            PackageItemLineEntity line = new PackageItemLineEntity();
            line.setIdempotencyKey(resolveIdempotencyKey(request.getIdempotencyKey(), routeCode, itemNo));
            line.setItemNo(itemNo);
            line.setActionType(SortingConstants.LINE_ACTION_LOAD);
            line.setEventType(SortingConstants.EVENT_ROUTE);
            line.setScanMode(SortingConstants.SCAN_MODE_BLIND);
            line.setToPackageNo(routeCode);
            line.setTargetCenterCode(routeCode);
            line.setStationCode(request.getStationCode());
            line.setOperatorId(request.getOperatorId());
            line.setDeviceId(request.getDeviceId());
            line.setExt("{\"securityStatus\":\"" + (danger ? SortingConstants.SECURITY_STATUS_DANGER : SortingConstants.SECURITY_STATUS_PASS)
                    + "\",\"routeCode\":\"" + escapeJson(routeResult.getRouteCode())
                    + "\",\"transportMode\":\"" + escapeJson(routeResult.getTransportMode())
                    + "\",\"nextHop\":\"" + escapeJson(nextHop)
                    + "\",\"sourceImage\":\"" + escapeJson(xray.getSourceImageName())
                    + "\",\"dangerLabels\":\"" + escapeJson(String.join(",", dangerLabels)) + "\"}");
            sortingRepository.savePackageItemLine(line);
        }

        if (danger) {
            saveSecurityDiscrepancy(routeCode, itemNo, xray, dangerLabels);
        }

        if (omsClient != null && !manualReview) {
            com.liana.post.oms.model.dto.MailRouteAssignRequest routeRequest = new com.liana.post.oms.model.dto.MailRouteAssignRequest();
            routeRequest.setCurrentSlot(routeCode);
            routeRequest.setDestinationNode(targetNode);
            routeRequest.setCurrentFacilityCode(request.getStationCode());
            routeRequest.setStatus(danger ? com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_RETURNED : com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_SORTED);
            omsClient.updateMailRoute(itemNo, routeRequest);
        }

        SortingSecurityAuditResponse response = new SortingSecurityAuditResponse();
        response.setItemNo(itemNo);
        response.setSecurityStatus(danger ? SortingConstants.SECURITY_STATUS_DANGER : manualReview ? "MANUAL_REVIEW" : SortingConstants.SECURITY_STATUS_PASS);
        response.setRouteCode(routeResult.getRouteCode());
        response.setSlotCode(routeCode);
        response.setTransportMode(routeResult.getTransportMode());
        response.setNextHop(nextHop);
        response.setMessage(message);
        response.setXrayImage(xray.getImageDataUrl());
        response.setSourceImageName(xray.getSourceImageName());
        response.setDetectedLabels(xray.getLabels());
        response.setDangerLabels(dangerLabels);
        response.setMailScope(mail == null ? null : mail.getMailScope());
        response.setServiceType(mail == null ? null : mail.getServiceType());
        response.setReturnToOrigin(danger);
        response.setOriginFacilityCode(mail == null ? null : mail.getOriginFacilityCode());
        response.setAuditedAt(LocalDateTime.now());
        return response;
    }

    @Override
    public List<SortingPendingRouteItemResponse> listPendingRouteItems(String stationCode) {
        String normalizedStation = StringUtils.hasText(stationCode) ? stationCode.trim().toUpperCase() : null;
        List<PackageItemLineEntity> lines = sortingRepository.findAllPackageLines().stream()
                .filter(line -> line != null && StringUtils.hasText(line.getItemNo()))
                .sorted(Comparator.comparing(PackageItemLineEntity::getEventTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, List<PackageItemLineEntity>> grouped = lines.stream()
                .collect(Collectors.groupingBy(line -> line.getItemNo().trim().toUpperCase(), LinkedHashMap::new, Collectors.toList()));
        Map<String, com.liana.post.oms.model.dto.MailResponse> mails = loadMailMap();
        List<SortingPendingRouteItemResponse> pending = new ArrayList<>();
        for (Map.Entry<String, List<PackageItemLineEntity>> entry : grouped.entrySet()) {
            PackageItemLineEntity latestUnpack = latestUnpackLine(entry.getValue(), normalizedStation);
            if (latestUnpack == null) {
                continue;
            }
            try { unpacking.requireMailReady(entry.getKey(), latestUnpack.getStationCode()); }
            catch (BusinessException e) { if (e.getCode() == 409) continue; throw e; }
            boolean routedAfterUnpack = entry.getValue().stream()
                    .anyMatch(line -> isLoadLine(line) && isAfter(line.getEventTime(), latestUnpack.getEventTime()));
            if (routedAfterUnpack) {
                continue;
            }
            SortingPendingRouteItemResponse response = new SortingPendingRouteItemResponse();
            response.setItemNo(entry.getKey());
            response.setSourcePackageNo(latestUnpack.getFromPackageNo());
            response.setManifestNo(latestUnpack.getManifestNo());
            response.setStationCode(latestUnpack.getStationCode());
            response.setUnpackedAt(latestUnpack.getEventTime());
            com.liana.post.oms.model.dto.MailResponse mail = mails.get(entry.getKey());
            if (mail != null) {
                response.setMailScope(mail.getMailScope());
                response.setServiceType(mail.getServiceType());
                response.setOriginFacilityCode(mail.getOriginFacilityCode());
                response.setCurrentFacilityCode(mail.getCurrentFacilityCode());
                response.setStatus(mail.getStatus());
            }
            pending.add(response);
        }
        return pending.stream()
                .sorted(Comparator.comparing(SortingPendingRouteItemResponse::getUnpackedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional
    public SortingRouteResponse routeCalculate(SortingRouteCalculateRequest request) {
        SortingRouteResponse response = new SortingRouteResponse();
        response.setRouteCode(request.getDestinationOrgCode() + "-" + normalizeBlank(request.getServiceLevel()) + "-01");
        response.setCellCode(request.getDestinationOrgCode() + "-CELL-01");
        response.setNextPackageNo(IdGeneratorUtil.generateBagNo());
        response.setDecision("ROUTE_OK");
        return response;
    }

    @Override
    public List<CountrySlotSummaryResponse> listCountrySlotSummaries(String stationCode) {
        List<com.liana.post.oms.model.dto.MailResponse> mails = loadInternationalMails(stationCode);
        if (mails.isEmpty()) {
            return List.of();
        }
        Map<String, List<com.liana.post.oms.model.dto.MailResponse>> grouped = mails.stream()
                .map(mail -> new java.util.AbstractMap.SimpleEntry<>(resolveCountrySlotKey(mail), mail))
                .filter(entry -> StringUtils.hasText(entry.getKey()))
                .collect(Collectors.groupingBy(java.util.Map.Entry::getKey, java.util.LinkedHashMap::new,
                        Collectors.mapping(java.util.Map.Entry::getValue, Collectors.toList())));
        Map<String, String> countryNames = loadCountryNames(grouped.keySet());
        return grouped.entrySet().stream()
                .map(entry -> toCountrySlotSummary(entry.getKey(), countryNames.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    @Override
    public List<SortingPackageResponse> sealCountrySlot(CountrySlotBulkRequest request) {
        throw new BusinessException(409, "旧国家格口直接封包已停用，请按实际下一跳使用结构化格口重封；不直接建立境外运输");
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SortingUnpackPreviewResponse previewUnpackItems(String packageNo, String manifestNo) {
        String normalizedPackageNo = normalizeBlank(packageNo);
        SortingUnpackPreviewResponse response = new SortingUnpackPreviewResponse();
        response.setPackageNo(normalizedPackageNo);

        String resolvedManifestNo = StringUtils.hasText(manifestNo) ? normalizeBlank(manifestNo) : null;
        if (resolvedManifestNo != null) {
            var manifest = sortingRepository.findManifestByNo(resolvedManifestNo)
                    .orElseThrow(() -> new BusinessException(404, "manifest not found"));
            if ("CANCELLED".equals(manifest.getManifestStatus())) throw new BusinessException(409, "cancelled manifest cannot be unpacked");
            if (sortingRepository.findManifestPackage(resolvedManifestNo, normalizedPackageNo).isEmpty()) {
                throw new BusinessException(409, "package does not belong to the specified manifest");
            }
            List<String> itemNos = loadPendingItemsFromManifest(resolvedManifestNo, normalizedPackageNo);
            response.setManifestNo(resolvedManifestNo);
            response.setSource("MANIFEST");
            response.setItemNos(itemNos);
            return response;
        }

        SortingManifestEntity inferredManifest = sortingRepository.findManifestByExpectedPackageNo(normalizedPackageNo).orElse(null);
        if (inferredManifest != null) {
            resolvedManifestNo = inferredManifest.getManifestNo();
            List<String> itemNos = loadPendingItemsFromManifest(resolvedManifestNo, normalizedPackageNo);
            if (!itemNos.isEmpty()) {
                response.setManifestNo(resolvedManifestNo);
                response.setSource("MANIFEST");
                response.setItemNos(itemNos);
                return response;
            }
        }

        List<String> itemNos = loadPendingItemsFromPackage(normalizedPackageNo);
        response.setManifestNo(resolvedManifestNo);
        response.setSource(itemNos.isEmpty() ? "EMPTY" : "OMS");
        response.setItemNos(itemNos);
        return response;
    }

    @Override
    public SortingPackageResponse reBag(SortingRebagRequest request) {
        return repacking.reserve(request.getIdempotencyKey(), request.getStationCode(), null,
                request.getRouteCode(), request.getSourcePackageNo(), request.getItemNos());
    }

    @Override
    public List<SortingSlotSummaryResponse> listSlotSummaries(String stationCode) {
        Map<String, SortingSlotSummaryResponse> summaries = new LinkedHashMap<>();
        for (SortingSlotSummaryResponse summary : listLocalSlotSummaries(stationCode)) {
            if (StringUtils.hasText(summary.getSlotCode())) {
                summaries.put(summary.getSlotCode().trim().toUpperCase(), summary);
            }
        }
        OmsClient client = omsClientProvider.getIfAvailable();
        String normalizedStation = StringUtils.hasText(stationCode) ? stationCode.trim().toUpperCase() : null;
        if (client != null) {
            com.liana.post.common.model.Result<List<MailSlotSummaryResponse>> result = client.listActiveSlots();
            if (result != null && result.getData() != null) {
                for (MailSlotSummaryResponse item : result.getData()) {
                    if (normalizedStation != null && !normalizedStation.equalsIgnoreCase(item.getCurrentFacilityCode())) {
                        continue;
                    }
                    SortingSlotSummaryResponse summary = toSortingSlotSummary(item);
                    if (summary.getPendingCount() != null && summary.getPendingCount() > 0 && StringUtils.hasText(summary.getSlotCode())) {
                        summaries.putIfAbsent(summary.getSlotCode().trim().toUpperCase(), summary);
                    }
                }
            }
        }
        return summaries.values().stream()
                .filter(item -> item.getPendingCount() != null && item.getPendingCount() > 0)
                .toList();
    }

    @Override
    public SortingPackageResponse sealBagBySlot(String slotCode, String stationCode, String operatorId, String requestNo) {
        return repacking.reserve(requestNo, stationCode, slotCode, null, null, null);
    }

    @Override
    public List<SortingPackageResponse> listPackages(String authorization) {
        String facilityCode = resolveFacilityCode(authorization);
        if (!StringUtils.hasText(facilityCode)) {
            return List.of();
        }
        List<SortingTotalPackageEntity> totalPackages = sortingRepository.findTotalPackagesByDestination(facilityCode);
        return totalPackages.stream()
                .filter(this::isVisibleTransitPackage)
                .map(this::toPackageResponse)
                .toList();
    }

    @Override
    public List<SortingManifestResponse> listManifests(boolean receiveCandidate, String authorization) {
        String facilityCode = receiveCandidate ? resolveFacilityCode(authorization) : null;
        return sortingRepository.findAllManifests().stream()
                .filter(manifest -> !receiveCandidate || isReceiveCandidateManifest(manifest, facilityCode))
                .map(this::toManifestResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SortingManifestDetailResponse getManifestDetail(String manifestNo) {
        SortingManifestEntity manifest = sortingRepository.findManifestByNo(manifestNo)
                .orElseThrow(() -> new BusinessException(404, "manifest not found: " + manifestNo));
        SortingManifestDetailResponse response = new SortingManifestDetailResponse();
        copyManifest(response, manifest);
        List<SortingManifestItemEntity> items = sortingRepository.findManifestItems(manifest.getManifestNo());
        response.setItems(items.stream()
                .map(this::toManifestItemResponse)
                .toList());
        List<SortingManifestPackageEntity> manifestPackages = sortingRepository.findManifestPackages(manifest.getManifestNo());
        List<SortingPackageResponse> packages;
        if (!manifestPackages.isEmpty()) {
            packages = new ArrayList<>(manifestPackages.stream()
                    .map(item -> sortingRepository.findTotalPackageByNo(item.getPackageNo())
                            .filter(pack -> manifest.getManifestNo().equals(pack.getManifestNo()))
                            .map(this::toPackageResponse)
                            .orElseGet(() -> toExpectedPackageResponse(manifest, item)))
                    .toList());
            Set<String> expectedPackageNos = manifestPackages.stream()
                    .map(SortingManifestPackageEntity::getPackageNo)
                    .map(value -> value.trim().toUpperCase())
                    .collect(Collectors.toSet());
            sortingRepository.findTotalPackagesByManifest(manifest.getManifestNo()).stream()
                    .filter(item -> !expectedPackageNos.contains(item.getPackageNo().trim().toUpperCase()))
                    .map(this::toPackageResponse)
                    .forEach(packages::add);
        } else {
            packages = expectedPackagesFromItems(manifest, items);
        }
        response.setPackages(packages);
        return response;
    }

    @Override
    public List<SortingLineResponse> listLines(String packageNo) {
        if (!StringUtils.hasText(packageNo)) {
            return sortingRepository.findAllPackageLines().stream().map(this::toLineResponse).toList();
        }
        return sortingRepository.findPackageLinesByPackage(packageNo).stream().map(this::toLineResponse).toList();
    }

    @Override
    public List<SortingDiscrepancyResponse> listDiscrepancies(String packageNo) {
        if (!StringUtils.hasText(packageNo)) {
            return sortingRepository.findAllDiscrepancies().stream().map(this::toDiscrepancyResponse).toList();
        }
        return sortingRepository.findDiscrepanciesByPackage(packageNo).stream().map(this::toDiscrepancyResponse).toList();
    }

    @Override
    public DashboardSummaryResponse dashboardSummary(String stationCode) {
        List<SortingTotalPackageEntity> packages = sortingRepository.findAllTotalPackages().stream()
                .filter(item -> isAllStation(stationCode) || matchesStation(item.getSourceOrgCode(), stationCode) || matchesStation(item.getDestinationOrgCode(), stationCode))
                .toList();
        List<SortingManifestEntity> manifests = sortingRepository.findAllManifests().stream()
                .filter(item -> isAllStation(stationCode) || matchesStation(item.getSourceOrgCode(), stationCode) || matchesStation(item.getDestinationOrgCode(), stationCode))
                .toList();
        List<PackageItemLineEntity> lines = sortingRepository.findAllPackageLines();
        List<DiscrepancyVerificationEntity> discrepancies = sortingRepository.findAllDiscrepancies();
        long opened = packages.stream().filter(item -> SortingConstants.PACKAGE_STATUS_OPENED.equals(item.getPackageStatus())).count();
        long sealed = packages.stream().filter(item -> SortingConstants.PACKAGE_STATUS_SEALED.equals(item.getPackageStatus())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("分拣数据");
        response.setScope(scopeLabel(stationCode));
        response.addMetric("总包数", packages.size(), "sorting_total_package 表", "info")
                .addMetric("已开拆", opened, "OPENED", "success")
                .addMetric("已封袋", sealed, "SEALED", "warning")
                .addMetric("明细行", lines.size(), "package_item_line 表", "neutral");
        response.addBreakdown("总包状态", countBy(packages.stream().map(SortingTotalPackageEntity::getPackageStatus).collect(Collectors.toList())));
        response.addBreakdown("路单状态", countBy(manifests.stream().map(SortingManifestEntity::getManifestStatus).collect(Collectors.toList())));
        response.addBreakdown("差异处理", List.of(DashboardSummaryResponse.item("差异记录", discrepancies.size(), "DISCREPANCY")));
        return response;
    }

    private boolean isAllStation(String stationCode) {
        return !StringUtils.hasText(stationCode);
    }

    private boolean matchesStation(String actual, String stationCode) {
        return StringUtils.hasText(actual) && StringUtils.hasText(stationCode) && actual.equalsIgnoreCase(stationCode.trim());
    }

    private String scopeLabel(String stationCode) {
        return isAllStation(stationCode) ? "全部机构" : stationCode.trim().toUpperCase();
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

    private List<String> loadPendingItemsFromManifest(String manifestNo, String packageNo) {
        String normalizedPackageNo = normalizeBlank(packageNo);
        return sortingRepository.findManifestItems(manifestNo).stream()
                .filter(item -> normalizedPackageNo.equalsIgnoreCase(normalizeBlank(item.getExpectedPackageNo())))
                .map(SortingManifestItemEntity::getItemNo)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> loadPendingItemsFromPackage(String packageNo) {
        Map<String, MailResponse> mails = loadMailMap();
        if (mails.isEmpty()) {
            return List.of();
        }
        String normalizedPackageNo = normalizeBlank(packageNo);
        return mails.values().stream()
                .filter(mail -> mail != null)
                .filter(mail -> normalizedPackageNo.equalsIgnoreCase(normalizeTrimmed(mail.getBagNo()))
                        || normalizedPackageNo.equalsIgnoreCase(normalizeTrimmed(mail.getPackageId())))
                .map(MailResponse::getWaybillNo)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .toList();
    }

    private List<String> resolveDangerLabels(com.liana.post.oms.model.dto.MailResponse mail, List<SortingDetectedLabelResponse> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        boolean internationalAir = mail != null
                && "INTERNATIONAL".equalsIgnoreCase(mail.getMailScope())
                && ("AIR".equalsIgnoreCase(mail.getServiceType()) || "SAL".equalsIgnoreCase(mail.getServiceType()));
        return labels.stream()
                .map(SortingDetectedLabelResponse::getLabel)
                .filter(StringUtils::hasText)
                .filter(label -> isGlobalDangerLabel(label) || (internationalAir && isInternationalAirDangerLabel(label)))
                .distinct()
                .toList();
    }

    private boolean isGlobalDangerLabel(String label) {
        return "Nonmetallic_Lighter".equals(label);
    }

    private boolean isInternationalAirDangerLabel(String label) {
        return "Laptop".equals(label)
                || "Mobile_Phone".equals(label)
                || "Tablet".equals(label)
                || "Portable_Charger_1".equals(label)
                || "Portable_Charger_2".equals(label);
    }

    private String resolveOriginFacilityCode(com.liana.post.oms.model.dto.MailResponse mail) {
        if (mail != null && StringUtils.hasText(mail.getOriginFacilityCode())) {
            return mail.getOriginFacilityCode().trim().toUpperCase();
        }
        return "ORIGIN_UNKNOWN";
    }

    private Map<String, com.liana.post.oms.model.dto.MailResponse> loadMailMap() {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return Map.of();
        }
        com.liana.post.common.model.Result<List<com.liana.post.oms.model.dto.MailResponse>> result = client.listMails();
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        return result.getData().stream()
                .filter(mail -> mail != null && StringUtils.hasText(mail.getWaybillNo()))
                .collect(Collectors.toMap(
                        mail -> mail.getWaybillNo().trim().toUpperCase(),
                        mail -> mail,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private PackageItemLineEntity latestUnpackLine(List<PackageItemLineEntity> lines, String stationCode) {
        if (lines == null) {
            return null;
        }
        return lines.stream()
                .filter(this::isUnpackLine)
                .filter(line -> !StringUtils.hasText(stationCode) || stationCode.equalsIgnoreCase(line.getStationCode()))
                .max(Comparator.comparing(PackageItemLineEntity::getEventTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isUnpackLine(PackageItemLineEntity line) {
        return line != null
                && (SortingConstants.EVENT_UNPACK.equalsIgnoreCase(line.getEventType())
                || SortingConstants.LINE_ACTION_UNLOAD.equalsIgnoreCase(line.getActionType()));
    }

    private boolean isLoadLine(PackageItemLineEntity line) {
        return line != null
                && (SortingConstants.EVENT_ROUTE.equalsIgnoreCase(line.getEventType())
                || SortingConstants.LINE_ACTION_LOAD.equalsIgnoreCase(line.getActionType()));
    }

    private boolean isAfter(LocalDateTime value, LocalDateTime baseline) {
        return value != null && (baseline == null || value.isAfter(baseline));
    }

    private void updateMailStatus(String waybillNo, String eventType, String stationCode) {
        TrackingClient trackingClient = trackingClientProvider.getIfAvailable();
        if (trackingClient == null) {
            return;
        }
        TrackingEventCreateRequest request = new TrackingEventCreateRequest();
        request.setWaybillNo(waybillNo);
        request.setEventType(eventType);
        request.setSourceService(SortingConstants.SOURCE_SORTING);
        request.setFacilityCode(stationCode);
        request.setOperatorId(null);
        request.setPayload("{}");
        trackingClient.recordEvent(request);
    }

    private void updateMailBag(String itemNo, String packageNo, String stationCode) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        com.liana.post.oms.model.dto.MailBagAssignRequest request = new com.liana.post.oms.model.dto.MailBagAssignRequest();
        request.setBagNo(StringUtils.hasText(packageNo) ? packageNo : null);
        request.setCurrentFacilityCode(StringUtils.hasText(stationCode) ? stationCode : null);
        client.updateMailBag(itemNo, request);
    }

    private void persistSlotSeal(String slotCode, String packageId, String stationCode) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        com.liana.post.oms.model.dto.MailSlotSealRequest request = new com.liana.post.oms.model.dto.MailSlotSealRequest();
        request.setPackageId(packageId);
        request.setDestinationNode(resolveDestinationNodeBySlot(slotCode));
        request.setCurrentFacilityCode(stationCode);
        request.setStatus(com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_DISPATCHED);
        client.sealMailSlot(slotCode, request);
    }

    private String resolveDestinationNodeBySlot(String slotCode) {
        String normalized = normalizeBlank(slotCode);
        if (normalized.endsWith("-SLOT")) {
            return normalized.substring(0, normalized.length() - 5);
        }
        if ("22".equals(normalized)) {
            return "A2";
        }
        if ("10".equals(normalized)) {
            return "B2";
        }
        return normalized;
    }

    private void recordTracking(String waybillNo, String eventType, String stationCode, String operatorId, String payload) {
        TrackingClient client = trackingClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        TrackingEventCreateRequest request = new TrackingEventCreateRequest();
        request.setWaybillNo(waybillNo);
        request.setEventType(eventType);
        request.setSourceService(SortingConstants.SOURCE_SORTING);
        request.setFacilityCode(stationCode);
        request.setOperatorId(parseOperatorId(operatorId));
        request.setPayload(payload);
        client.recordEvent(request);
    }

    private void saveDiscrepancy(String manifestNo, String packageNo, String itemNo, String type, String source, Integer expectedQty, Integer actualQty) {
        DiscrepancyVerificationEntity entity = new DiscrepancyVerificationEntity();
        entity.setManifestNo(manifestNo);
        entity.setPackageNo(packageNo);
        entity.setItemNo(itemNo);
        entity.setDiscrepancyType(type);
        entity.setDiscrepancySource(source);
        entity.setExpectedQty(expectedQty);
        entity.setActualQty(actualQty);
        entity.setExceptionLevel("NORMAL");
        entity.setStatus("OPEN");
        sortingRepository.saveDiscrepancy(entity);
    }

    private void saveSecurityDiscrepancy(String packageNo, String itemNo, XraySecurityAuditSimulator.XrayAuditSample xray, List<String> dangerLabels) {
        DiscrepancyVerificationEntity entity = new DiscrepancyVerificationEntity();
        entity.setManifestNo(null);
        entity.setPackageNo(packageNo);
        entity.setItemNo(itemNo);
        entity.setDiscrepancyType(SortingConstants.DISCREPANCY_SECURITY_FAILED);
        entity.setDiscrepancySource(SortingConstants.SOURCE_SECURITY);
        entity.setExpectedQty(1);
        entity.setActualQty(0);
        entity.setExceptionLevel("DANGER");
        entity.setStatus("OPEN");
        entity.setEvidence("{\"sourceImage\":\"" + escapeJson(xray.getSourceImageName())
                + "\",\"dangerLabels\":\"" + escapeJson(String.join(",", dangerLabels)) + "\"}");
        sortingRepository.saveDiscrepancy(entity);
    }

    private String resolveSlotCode(String itemNo, String destinationOrgCode) {
        String digits = itemNo.replaceAll("\\D", "");
        String suffix = digits.length() >= 2 ? digits.substring(digits.length() - 2) : String.format("%02d", Math.abs(itemNo.hashCode()) % 100);
        return StringUtils.hasText(destinationOrgCode) ? destinationOrgCode.trim().toUpperCase() + "-SLOT-" + suffix : "SLOT-" + suffix;
    }

    private String escapeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<SortingSlotSummaryResponse> listLocalSlotSummaries(String stationCode) {
        Map<String, List<PackageItemLineEntity>> grouped = listPendingSlotLines(stationCode).stream()
                .collect(Collectors.groupingBy(line -> line.getTargetCenterCode().trim().toUpperCase(), LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> toSlotSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<PackageItemLineEntity> listPendingSlotLines(String stationCode) {
        return sortingRepository.findAllPackageLines().stream()
                .filter(line -> line != null && StringUtils.hasText(line.getItemNo()))
                .filter(line -> SortingConstants.LINE_ACTION_LOAD.equalsIgnoreCase(line.getActionType()))
                .collect(Collectors.groupingBy(line -> line.getItemNo().trim().toUpperCase(), LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .map(lines -> lines.stream()
                        .max(Comparator.comparing(PackageItemLineEntity::getEventTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null))
                .filter(line -> isPendingSlotLine(line, stationCode))
                .toList();
    }

    private boolean isPendingSlotLine(PackageItemLineEntity line, String stationCode) {
        return line != null
                && SortingConstants.EVENT_ROUTE.equalsIgnoreCase(line.getEventType())
                && SortingConstants.LINE_ACTION_LOAD.equalsIgnoreCase(line.getActionType())
                && StringUtils.hasText(line.getTargetCenterCode())
                && (!StringUtils.hasText(stationCode) || stationCode.equalsIgnoreCase(line.getStationCode()))
                && (line.getExt() == null || !line.getExt().contains("\"countryCode\""));
    }

    private SortingSlotSummaryResponse toSlotSummary(String slotCode, List<PackageItemLineEntity> lines) {
        SortingSlotSummaryResponse response = new SortingSlotSummaryResponse();
        response.setSlotCode(slotCode);
        response.setDestinationOrgCode(resolveDestinationNodeBySlot(slotCode));
        List<String> items = lines.stream()
                .map(PackageItemLineEntity::getItemNo)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        response.setPendingCount(items.size());
        response.setPreviewItemNos(items.stream().limit(5).toList());
        response.setPreviewItemNo(items.isEmpty() ? null : items.get(0));
        response.setRouteCode(slotCode);
        response.setBagStatus(SortingConstants.PACKAGE_STATUS_RAW);
        return response;
    }

    private SortingSlotSummaryResponse toSortingSlotSummary(MailSlotSummaryResponse summary) {
        SortingSlotSummaryResponse response = new SortingSlotSummaryResponse();
        response.setSlotCode(summary.getSlotCode());
        response.setDestinationOrgCode(summary.getDestinationNode());
        response.setPendingCount(summary.getPendingCount());
        response.setPreviewItemNos(summary.getPreviewItemNos());
        response.setPreviewItemNo(summary.getPreviewItemNos() == null || summary.getPreviewItemNos().isEmpty() ? null : summary.getPreviewItemNos().get(0));
        response.setRouteCode(summary.getNextHopCode());
        response.setBagStatus(summary.getBagStatus());
        return response;
    }

    private List<String> dedupe(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private boolean isManifestChecked(SortingManifestEntity manifest) {
        return manifest != null && SortingConstants.MANIFEST_STATUS_CHECKED.equalsIgnoreCase(manifest.getManifestStatus());
    }

    private boolean isManifestReceiveComplete(SortingManifestEntity manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.getManifestNo())) {
            return false;
        }
        List<SortingManifestPackageEntity> expectedPackages = sortingRepository.findManifestPackages(manifest.getManifestNo());
        if (expectedPackages.isEmpty()) {
            return false;
        }
        Set<String> expected = expectedPackages.stream()
                .map(SortingManifestPackageEntity::getPackageNo)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .collect(Collectors.toSet());
        Set<String> received = sortingRepository.findTotalPackagesByManifest(manifest.getManifestNo()).stream()
                .filter(this::isReceiveCheckedPackage)
                .map(SortingTotalPackageEntity::getPackageNo)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .collect(Collectors.toSet());
        return !expected.isEmpty() && expected.equals(received);
    }

    private boolean isReceiveCheckedPackage(SortingTotalPackageEntity totalPackage) {
        if (totalPackage == null || !StringUtils.hasText(totalPackage.getPackageStatus())) {
            return false;
        }
        String status = totalPackage.getPackageStatus();
        return SortingConstants.PACKAGE_STATUS_RECEIVED.equalsIgnoreCase(status)
                || SortingConstants.PACKAGE_STATUS_OPENING.equalsIgnoreCase(status)
                || SortingConstants.PACKAGE_STATUS_OPENED.equalsIgnoreCase(status);
    }

    private boolean isReceiveCandidateManifest(SortingManifestEntity manifest, String facilityCode) {
        if (manifest == null) {
            return false;
        }
        if (!SortingConstants.PACKAGE_LEVEL_TRANSIT.equalsIgnoreCase(manifest.getManifestType())) {
            return false;
        }
        if (!manifestPrealertService.isSealed(manifest.getManifestNo())) return false;
        if (java.util.Set.of("CLOSING", "CLOSED", "CANCELLED").contains(manifest.getManifestStatus()) || receiving.isCheckFinished(manifest.getManifestNo())) return false;
        if (isManifestChecked(manifest) || isManifestReceiveComplete(manifest)) {
            return false;
        }
        if (StringUtils.hasText(facilityCode) && !facilityCode.trim().equalsIgnoreCase(manifest.getDestinationOrgCode())) {
            return false;
        }
        List<SortingManifestPackageEntity> expectedPackages = sortingRepository.findManifestPackages(manifest.getManifestNo());
        if (!expectedPackages.isEmpty()) {
            return !isManifestChecked(manifest)
                    && expectedPackages.stream().anyMatch(item -> !"RECEIVED".equalsIgnoreCase(item.getItemStatus()));
        }
        List<SortingTotalPackageEntity> packages = sortingRepository.findTotalPackagesByManifest(manifest.getManifestNo());
        return packages.isEmpty() || packages.stream().anyMatch(this::isVisibleTransitPackage);
    }

    private String normalizeScanMode(String scanMode) {
        String normalized = normalizeBlank(scanMode);
        if (!SortingConstants.SCAN_MODE_PREALERT.equals(normalized)
                && !SortingConstants.SCAN_MODE_BLIND.equals(normalized)
                && !SortingConstants.SCAN_MODE_FORCE.equals(normalized)) {
            throw new BusinessException(400, "unsupported scan mode");
        }
        return normalized;
    }

    private String normalizePackageLevel(String packageLevel) {
        String normalized = normalizeBlank(packageLevel);
        if (!SortingConstants.PACKAGE_LEVEL_EXPORT.equals(normalized)
                && !SortingConstants.PACKAGE_LEVEL_TRANSIT.equals(normalized)
                && !SortingConstants.PACKAGE_LEVEL_IMPORT.equals(normalized)) {
            throw new BusinessException(400, "unsupported package level");
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "value cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeTrimmed(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String resolveFacilityCode(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String token = authorization.replaceFirst("(?i)^Bearer\\s+", "");
        return JwtTokenUtil.getFacilityCodeFromToken(token);
    }

    private boolean isVisibleTransitPackage(SortingTotalPackageEntity entity) {
        if (entity == null) {
            return false;
        }
        if (!SortingConstants.PACKAGE_LEVEL_TRANSIT.equalsIgnoreCase(entity.getPackageLevel())) {
            return false;
        }
        return StringUtils.hasText(entity.getPackageNo()) && entity.getPackageNo().matches("^B\\d{21}$");
    }

    private String resolveIdempotencyKey(String key, String packageNo, String itemNo) {
        return StringUtils.hasText(key) ? key.trim() + ":" + packageNo + ":" + itemNo : packageNo + ":" + itemNo;
    }

    private SortingPackageResponse toPackageResponse(SortingTotalPackageEntity entity) {
        SortingPackageResponse response = new SortingPackageResponse();
        response.setId(entity.getId());
        response.setPackageNo(entity.getPackageNo());
        response.setPackageLevel(entity.getPackageLevel());
        response.setPackageStatus(entity.getPackageStatus());
        response.setSourceOrgCode(entity.getSourceOrgCode());
        response.setDestinationOrgCode(entity.getDestinationOrgCode());
        response.setParentPackageNo(entity.getParentPackageNo());
        response.setManifestNo(entity.getManifestNo());
        response.setPrealertFlag(entity.getPrealertFlag());
        response.setSealedAt(entity.getSealedAt());
        response.setDispatchedAt(entity.getDispatchedAt());
        response.setReceivedAt(entity.getReceivedAt());
        response.setOpenedAt(entity.getOpenedAt());
        response.setTerminalReason(entity.getTerminalReason());
        return response;
    }

    private List<SortingPackageResponse> expectedPackagesFromItems(SortingManifestEntity manifest, List<SortingManifestItemEntity> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(SortingManifestItemEntity::getExpectedPackageNo)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .map(packageNo -> {
                    SortingPackageResponse response = new SortingPackageResponse();
                    response.setPackageNo(packageNo);
                    response.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
                    response.setPackageStatus(SortingConstants.PACKAGE_STATUS_RAW);
                    response.setSourceOrgCode(manifest.getSourceOrgCode());
                    response.setDestinationOrgCode(manifest.getDestinationOrgCode());
                    response.setManifestNo(manifest.getManifestNo());
                    response.setPrealertFlag(1);
                    return response;
                })
                .toList();
    }

    private SortingPackageResponse toExpectedPackageResponse(SortingManifestEntity manifest, SortingManifestPackageEntity item) {
        SortingPackageResponse response = new SortingPackageResponse();
        response.setPackageNo(item.getPackageNo());
        response.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
        response.setPackageStatus(item.getItemStatus() == null ? "EXPECTED" : item.getItemStatus());
        response.setReceivedAt(item.getReceivedAt());
        response.setSourceOrgCode(manifest.getSourceOrgCode());
        response.setDestinationOrgCode(manifest.getDestinationOrgCode());
        response.setManifestNo(manifest.getManifestNo());
        response.setPrealertFlag(1);
        return response;
    }

    private List<com.liana.post.oms.model.dto.MailResponse> loadInternationalMails(String stationCode) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return List.of();
        }
        com.liana.post.common.model.Result<List<com.liana.post.oms.model.dto.MailResponse>> result = client.listMails();
        if (result == null || result.getData() == null) {
            return List.of();
        }
        String normalizedStation = StringUtils.hasText(stationCode) ? stationCode.trim().toUpperCase() : null;
        Map<String, List<PackageItemLineEntity>> linesByItem = sortingRepository.findAllPackageLines().stream()
                .filter(line -> line != null && StringUtils.hasText(line.getItemNo()))
                .collect(Collectors.groupingBy(line -> line.getItemNo().trim().toUpperCase(), LinkedHashMap::new, Collectors.toList()));
        return result.getData().stream()
                .filter(mail -> mail != null)
                .filter(mail -> StringUtils.hasText(mail.getCurrentFacilityCode()))
                .filter(mail -> !StringUtils.hasText(stationCode) || stationCode.equalsIgnoreCase(mail.getCurrentFacilityCode()))
                .filter(mail -> "INTERNATIONAL".equalsIgnoreCase(mail.getMailScope()))
                .filter(mail -> com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_SORTED.equals(mail.getStatus())
                        || com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_ARRIVED.equals(mail.getStatus())
                        || com.liana.post.common.constant.LogisticsConstants.MAIL_STATUS_CREATED.equals(mail.getStatus()))
                .filter(mail -> isCountrySlotCandidate(mail, normalizedStation, linesByItem))
                .toList();
    }

    private boolean isCountrySlotCandidate(com.liana.post.oms.model.dto.MailResponse mail, String stationCode, Map<String, List<PackageItemLineEntity>> linesByItem) {
        if (mail == null) {
            return false;
        }
        if (!StringUtils.hasText(mail.getPackageId())) {
            return true;
        }
        if (!StringUtils.hasText(mail.getWaybillNo()) || linesByItem == null || linesByItem.isEmpty()) {
            return false;
        }
        List<PackageItemLineEntity> lines = linesByItem.get(mail.getWaybillNo().trim().toUpperCase());
        PackageItemLineEntity latestUnpack = latestUnpackLine(lines, stationCode);
        if (latestUnpack == null) {
            return false;
        }
        return lines.stream()
                .noneMatch(line -> isLoadLine(line) && isAfter(line.getEventTime(), latestUnpack.getEventTime()));
    }

    private Map<String, String> loadCountryNames(Set<String> countryCodes) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null || countryCodes == null || countryCodes.isEmpty()) {
            return Map.of();
        }
        com.liana.post.common.model.Result<List<com.liana.post.oms.model.dto.CountryResponse>> result = client.listCountries();
        if (result == null || result.getData() == null) {
            return countryCodes.stream().collect(Collectors.toMap(code -> code, code -> code, (a, b) -> a, java.util.LinkedHashMap::new));
        }
        Map<String, String> names = result.getData().stream()
                .filter(country -> country != null && StringUtils.hasText(country.getCode()))
                .collect(Collectors.toMap(
                        country -> country.getCode().trim().toUpperCase(),
                        country -> StringUtils.hasText(country.getName()) ? country.getName() : country.getCode(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
        for (String code : countryCodes) {
            names.putIfAbsent(code, code);
        }
        return names;
    }

    private CountrySlotSummaryResponse toCountrySlotSummary(String countryCode, String countryName, List<com.liana.post.oms.model.dto.MailResponse> mails) {
        CountrySlotSummaryResponse response = new CountrySlotSummaryResponse();
        response.setCountryCode(countryCode);
        response.setCountryName(StringUtils.hasText(countryName) ? countryName : countryCode);
        response.setPendingCount(mails == null ? 0 : mails.size());
        List<String> preview = mails == null ? List.of() : mails.stream()
                .map(com.liana.post.oms.model.dto.MailResponse::getWaybillNo)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
        response.setPreviewItemNos(preview);
        response.setPreviewItemNo(preview.isEmpty() ? null : preview.get(0));
        response.setBagStatus(SortingConstants.PACKAGE_STATUS_RAW);
        response.setExportFacilityCode(resolveExportFacilityCode(countryCode));
        response.setRouteCode(countryCode + "-EXPORT");
        return response;
    }

    private String resolveExportFacilityCode(String countryCode) {
        return "A2";
    }

    private String resolveCountrySlotKey(com.liana.post.oms.model.dto.MailResponse mail) {
        if (mail == null) {
            return "";
        }
        if (StringUtils.hasText(mail.getCurrentSlot())) {
            return normalizeCountryCode(mail.getCurrentSlot());
        }
        return normalizeCountryCode(mail.getDestCountryCode());
    }

    private String normalizeCountryCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase();
    }

    private SortingLineResponse toLineResponse(PackageItemLineEntity entity) {
        SortingLineResponse response = new SortingLineResponse();
        response.setId(entity.getId());
        response.setBizLineNo(entity.getBizLineNo());
        response.setItemNo(entity.getItemNo());
        response.setActionType(entity.getActionType());
        response.setEventType(entity.getEventType());
        response.setScanMode(entity.getScanMode());
        response.setFromPackageNo(entity.getFromPackageNo());
        response.setToPackageNo(entity.getToPackageNo());
        response.setManifestNo(entity.getManifestNo());
        response.setScanBatchNo(entity.getScanBatchNo());
        response.setStationCode(entity.getStationCode());
        response.setSourceCenterCode(entity.getSourceCenterCode());
        response.setTargetCenterCode(entity.getTargetCenterCode());
        response.setOperatorId(entity.getOperatorId());
        response.setDeviceId(entity.getDeviceId());
        response.setExt(entity.getExt());
        response.setEventTime(entity.getEventTime());
        return response;
    }

    private SortingDiscrepancyResponse toDiscrepancyResponse(DiscrepancyVerificationEntity entity) {
        SortingDiscrepancyResponse response = new SortingDiscrepancyResponse();
        response.setId(entity.getId());
        response.setVerificationNo(entity.getVerificationNo());
        response.setManifestNo(entity.getManifestNo());
        response.setPackageNo(entity.getPackageNo());
        response.setItemNo(entity.getItemNo());
        response.setDiscrepancyType(entity.getDiscrepancyType());
        response.setDiscrepancySource(entity.getDiscrepancySource());
        response.setExpectedQty(entity.getExpectedQty());
        response.setActualQty(entity.getActualQty());
        response.setExceptionLevel(entity.getExceptionLevel());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    private SortingManifestResponse toManifestResponse(SortingManifestEntity entity) {
        SortingManifestResponse response = new SortingManifestResponse();
        copyManifest(response, entity);
        return response;
    }

    private void copyManifest(SortingManifestResponse response, SortingManifestEntity entity) {
        response.setId(entity.getId());
        response.setManifestNo(entity.getManifestNo());
        response.setManifestType(entity.getManifestType());
        response.setSourceOrgCode(entity.getSourceOrgCode());
        response.setDestinationOrgCode(entity.getDestinationOrgCode());
        response.setBatchNo(entity.getBatchNo());
        response.setManifestStatus(entity.getManifestStatus());
        response.setVersionNo(entity.getVersionNo());
        response.setPrealertFlag(entity.getPrealertFlag());
        response.setExpectedPackageQty(entity.getExpectedPackageQty());
        response.setExpectedItemQty(entity.getExpectedItemQty());
        response.setEtaTime(entity.getEtaTime());
    }

    private SortingManifestItemResponse toManifestItemResponse(SortingManifestItemEntity entity) {
        SortingManifestItemResponse response = new SortingManifestItemResponse();
        response.setId(entity.getId());
        response.setManifestNo(entity.getManifestNo());
        response.setItemNo(entity.getItemNo());
        response.setExpectedPackageNo(entity.getExpectedPackageNo());
        response.setExpectedSeqNo(entity.getExpectedSeqNo());
        response.setExpectedRouteCode(entity.getExpectedRouteCode());
        response.setExpectedQty(entity.getExpectedQty());
        response.setItemStatus(entity.getItemStatus());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    private Long parseOperatorId(String operatorId) {
        if (!StringUtils.hasText(operatorId)) {
            return null;
        }
        try {
            return Long.parseLong(operatorId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
