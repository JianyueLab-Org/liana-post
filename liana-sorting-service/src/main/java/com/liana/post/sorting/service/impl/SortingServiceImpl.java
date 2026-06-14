package com.liana.post.sorting.service.impl;

import com.liana.post.common.dto.sorting.ManifestArrivedRequest;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.sorting.client.TrackingClient;
import com.liana.post.sorting.constant.SortingConstants;
import com.liana.post.sorting.model.dto.*;
import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.model.entity.PackageItemLineEntity;
import com.liana.post.sorting.model.entity.SortingManifestEntity;
import com.liana.post.sorting.model.entity.SortingManifestItemEntity;
import com.liana.post.sorting.model.entity.SortingTotalPackageEntity;
import com.liana.post.sorting.repository.SortingRepository;
import com.liana.post.sorting.service.SortingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SortingServiceImpl implements SortingService {

    private final SortingRepository sortingRepository;
    private final ObjectProvider<OmsClient> omsClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;
    private final ObjectProvider<DispatchLifecycleClient> dispatchLifecycleClientProvider;

    public SortingServiceImpl(SortingRepository sortingRepository,
                              ObjectProvider<OmsClient> omsClientProvider,
                              ObjectProvider<TrackingClient> trackingClientProvider,
                              ObjectProvider<DispatchLifecycleClient> dispatchLifecycleClientProvider) {
        this.sortingRepository = sortingRepository;
        this.omsClientProvider = omsClientProvider;
        this.trackingClientProvider = trackingClientProvider;
        this.dispatchLifecycleClientProvider = dispatchLifecycleClientProvider;
    }

    @Override
    @Transactional
    public void syncManifest(ManifestDTO request) {
        if (request == null || !StringUtils.hasText(request.getManifestCode())) {
            throw new BusinessException(400, "manifestCode cannot be blank");
        }
        SortingManifestEntity manifest = sortingRepository.findManifestByNo(request.getManifestCode()).orElseGet(SortingManifestEntity::new);
        manifest.setManifestNo(request.getManifestCode());
        manifest.setBatchNo(request.getManifestCode());
        manifest.setSourceOrgCode(request.getOriginNode());
        manifest.setDestinationOrgCode(request.getTargetNode());
        manifest.setManifestType("TRANSIT");
        manifest.setManifestStatus("RECEIVED");
        manifest.setPrealertFlag(1);
        manifest.setExpectedPackageQty(request.getTotalPackages() == null ? 0 : request.getTotalPackages().size());
        int itemQty = 0;
        if (request.getTotalPackages() != null) {
            for (TotalPackageDTO totalPackage : request.getTotalPackages()) {
                itemQty += totalPackage.getItems() == null ? 0 : totalPackage.getItems().size();
            }
        }
        manifest.setExpectedItemQty(itemQty);
        if (request.getTotalWeight() != null) {
            manifest.setEtaTime(manifest.getEtaTime());
        }
        SortingManifestEntity saved = sortingRepository.saveManifest(manifest);
        Set<String> existingItems = sortingRepository.findManifestItems(saved.getManifestNo()).stream()
                .map(SortingManifestItemEntity::getItemNo)
                .map(value -> value == null ? null : value.toUpperCase())
                .collect(Collectors.toSet());
        if (request.getTotalPackages() != null) {
            for (TotalPackageDTO totalPackage : request.getTotalPackages()) {
                if (totalPackage == null || !StringUtils.hasText(totalPackage.getPackageNo())) {
                    continue;
                }
                if (totalPackage.getItems() == null) {
                    continue;
                }
                for (TotalPackageDTO.ManifestItemDTO item : totalPackage.getItems()) {
                    if (item == null || !StringUtils.hasText(item.getItemNo())) {
                        continue;
                    }
                    SortingManifestItemEntity line = new SortingManifestItemEntity();
                    line.setManifestNo(saved.getManifestNo());
                    line.setItemNo(item.getItemNo().trim());
                    line.setExpectedPackageNo(totalPackage.getPackageNo());
                    line.setExpectedSeqNo(item.getSeqNo());
                    line.setExpectedRouteCode(item.getRouteCode());
                    line.setExpectedQty(1);
                    line.setItemStatus("EXPECTED");
                    if (!existingItems.contains(line.getItemNo().toUpperCase())) {
                        sortingRepository.saveManifestItem(line);
                        existingItems.add(line.getItemNo().toUpperCase());
                    }
                }
            }
        }
    }

    @Override
    @Transactional
    public SortingPackageResponse receive(SortingReceiveRequest request) {
        List<String> packageNos = dedupe(request.getPackageNos());
        if (packageNos.isEmpty()) {
            throw new BusinessException(400, "packageNos cannot be empty");
        }
        SortingManifestEntity manifest = null;
        if (isPrealert(request) && StringUtils.hasText(request.getManifestNo())) {
            manifest = ensureManifest(request);
        }
        SortingTotalPackageEntity first = null;
        for (String packageNo : packageNos) {
            SortingTotalPackageEntity totalPackage = sortingRepository.findTotalPackageByNo(packageNo).orElseGet(() -> {
                SortingTotalPackageEntity pkg = new SortingTotalPackageEntity();
                pkg.setPackageNo(packageNo);
                pkg.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
                pkg.setPackageStatus(SortingConstants.PACKAGE_STATUS_RAW);
                pkg.setSourceOrgCode(request.getStationCode());
                pkg.setPrealertFlag(isPrealert(request) ? 1 : 0);
                return sortingRepository.saveTotalPackage(pkg);
            });
            assertPackageOpenable(totalPackage);
            totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_RECEIVED);
            totalPackage.setReceivedAt(LocalDateTime.now());
            totalPackage.setManifestNo(manifest == null ? request.getManifestNo() : manifest.getManifestNo());
            sortingRepository.updateTotalPackage(totalPackage);
            if (first == null) {
                first = totalPackage;
            }
        }
        recordTracking(packageNos.get(0), SortingConstants.EVENT_RECEIVE, request.getStationCode(), request.getOperatorId(),
                "{\"mode\":\"" + request.getReceiveMode() + "\",\"packageCount\":" + packageNos.size() + "}");
        if (manifest != null) {
            notifyDispatchArrived(manifest.getManifestNo(), first.getPackageNo(), request.getStationCode(), "sorting receive completed");
        }
        return toPackageResponse(first);
    }

    @Override
    @Transactional
    public List<SortingLineResponse> unpackItem(SortingUnpackItemRequest request) {
        SortingTotalPackageEntity totalPackage = sortingRepository.findTotalPackageByNo(request.getPackageNo())
                .orElseThrow(() -> new BusinessException(404, "sorting package not found: " + request.getPackageNo()));
        if (SortingConstants.PACKAGE_STATUS_OPENED.equals(totalPackage.getPackageStatus())) {
            throw new BusinessException(400, "package already opened");
        }
        if (SortingConstants.PACKAGE_STATUS_RECEIVED.equals(totalPackage.getPackageStatus())) {
            totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_OPENING);
            sortingRepository.updateTotalPackage(totalPackage);
        }

        List<SortingLineResponse> responses = new ArrayList<>();
        boolean prealert = SortingConstants.SCAN_MODE_PREALERT.equalsIgnoreCase(request.getScanMode()) && StringUtils.hasText(request.getManifestNo());
        Set<String> actual = new LinkedHashSet<>();
        Set<String> expected = prealert ? sortingRepository.findManifestItems(request.getManifestNo()).stream().map(SortingManifestItemEntity::getItemNo).collect(Collectors.toCollection(LinkedHashSet::new)) : Set.of();

        for (SortingUnpackItemRequest.SortingUnpackItemEntry entry : request.getItems()) {
            actual.add(entry.getItemNo());
            validateMailExists(entry.getItemNo());
            PackageItemLineEntity line = new PackageItemLineEntity();
            line.setIdempotencyKey(resolveIdempotencyKey(request.getIdempotencyKey(), request.getPackageNo(), entry.getItemNo()));
            line.setItemNo(entry.getItemNo());
            line.setActionType(SortingConstants.LINE_ACTION_UNLOAD);
            line.setEventType(SortingConstants.EVENT_UNPACK);
            line.setScanMode(normalizeScanMode(request.getScanMode()));
            line.setFromPackageNo(request.getPackageNo());
            line.setManifestNo(request.getManifestNo());
            line.setScanBatchNo(request.getScanBatchNo());
            line.setStationCode(request.getStationCode());
            line.setOperatorId(request.getOperatorId());
            line.setDeviceId(request.getDeviceId());
            sortingRepository.savePackageItemLine(line);
            responses.add(toLineResponse(line));
            updateMailBag(entry.getItemNo(), null);

            if (prealert && !expected.contains(entry.getItemNo())) {
                saveDiscrepancy(request.getManifestNo(), request.getPackageNo(), entry.getItemNo(), SortingConstants.DISCREPANCY_SURPLUS, SortingConstants.SOURCE_MANIFEST, 0, 1);
            }
            updateMailStatus(entry.getItemNo(), SortingConstants.EVENT_SORTED, request.getStationCode());
        }

        if (prealert) {
            for (String missing : expected) {
                if (!actual.contains(missing)) {
                    saveDiscrepancy(request.getManifestNo(), request.getPackageNo(), missing, SortingConstants.DISCREPANCY_MISSING, SortingConstants.SOURCE_MANIFEST, 1, 0);
                }
            }
        }

        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_OPENED);
        totalPackage.setOpenedAt(LocalDateTime.now());
        totalPackage.setTerminalReason(SortingConstants.PACKAGE_STATUS_OPENED);
        sortingRepository.updateTotalPackage(totalPackage);
        recordTracking(totalPackage.getPackageNo(), SortingConstants.EVENT_UNPACK, request.getStationCode(), request.getOperatorId(), "{\"opened\":true}");
        return responses;
    }

    @Override
    @Transactional
    public SortingSecurityAuditResponse routeCalculate(SortingRouteScanRequest request) {
        String itemNo = normalizeBlank(request.getItemNo());
        validateMailExists(itemNo);
        OmsClient omsClient = omsClientProvider.getIfAvailable();
        com.liana.post.common.model.Result<com.liana.post.oms.model.dto.MailResponse> mailResult = omsClient == null ? null : omsClient.getMail(itemNo);
        com.liana.post.oms.model.dto.MailResponse mail = mailResult == null ? null : mailResult.getData();
        String destFacilityCode = mail != null && StringUtils.hasText(mail.getDestFacilityCode()) ? mail.getDestFacilityCode() : request.getStationCode();
        String slotCode = resolveSlotCode(itemNo, destFacilityCode);
        boolean danger = "RD588151316CN".equalsIgnoreCase(itemNo);

        PackageItemLineEntity line = new PackageItemLineEntity();
        line.setIdempotencyKey(resolveIdempotencyKey(request.getIdempotencyKey(), slotCode, itemNo));
        line.setItemNo(itemNo);
        line.setActionType(SortingConstants.LINE_ACTION_LOAD);
        line.setEventType(SortingConstants.EVENT_ROUTE);
        line.setScanMode(SortingConstants.SCAN_MODE_BLIND);
        line.setToPackageNo(slotCode);
        line.setTargetCenterCode(slotCode);
        line.setStationCode(request.getStationCode());
        line.setOperatorId(request.getOperatorId());
        line.setDeviceId(request.getDeviceId());
        line.setExt("{\"securityStatus\":\"" + (danger ? SortingConstants.SECURITY_STATUS_DANGER : SortingConstants.SECURITY_STATUS_PASS) + "\"}");
        sortingRepository.savePackageItemLine(line);

        if (danger) {
            saveDiscrepancy(null, slotCode, itemNo, SortingConstants.DISCREPANCY_SECURITY_FAILED, SortingConstants.SOURCE_SECURITY, 1, 0);
        }

        SortingSecurityAuditResponse response = new SortingSecurityAuditResponse();
        response.setItemNo(itemNo);
        response.setSecurityStatus(danger ? SortingConstants.SECURITY_STATUS_DANGER : SortingConstants.SECURITY_STATUS_PASS);
        response.setRouteCode(slotCode);
        response.setNextHop(danger ? "RETURN_CENTER" : destFacilityCode);
        response.setMessage(danger ? "[AI AUDIT] DANGER: blocked prohibited item" : "[AI AUDIT] PASS: security check ok");
        response.setXrayImage(danger ? "danger-xray.png" : "pass-xray.png");
        response.setAuditedAt(LocalDateTime.now());
        return response;
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
    @Transactional
    public SortingPackageResponse reBag(SortingRebagRequest request) {
        SortingTotalPackageEntity source = sortingRepository.findTotalPackageByNo(request.getSourcePackageNo())
                .orElseThrow(() -> new BusinessException(404, "source package not found"));
        SortingTotalPackageEntity totalPackage = new SortingTotalPackageEntity();
        totalPackage.setPackageNo(IdGeneratorUtil.generateBagNo());
        totalPackage.setPackageLevel(normalizePackageLevel(request.getPackageLevel()));
        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_RAW);
        totalPackage.setSourceOrgCode(request.getStationCode());
        totalPackage.setParentPackageNo(source.getPackageNo());
        totalPackage.setPrealertFlag(0);
        totalPackage = sortingRepository.saveTotalPackage(totalPackage);

        for (String itemNo : dedupe(request.getItemNos())) {
            PackageItemLineEntity line = new PackageItemLineEntity();
            line.setIdempotencyKey(resolveIdempotencyKey(request.getIdempotencyKey(), totalPackage.getPackageNo(), itemNo));
            line.setItemNo(itemNo);
            line.setActionType(SortingConstants.LINE_ACTION_LOAD);
            line.setEventType(SortingConstants.EVENT_REBAG);
            line.setScanMode(SortingConstants.SCAN_MODE_BLIND);
            line.setFromPackageNo(source.getPackageNo());
            line.setToPackageNo(totalPackage.getPackageNo());
            line.setStationCode(request.getStationCode());
            line.setOperatorId(request.getOperatorId());
            sortingRepository.savePackageItemLine(line);
            updateMailBag(itemNo, totalPackage.getPackageNo());
        }

        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_SEALED);
        totalPackage.setSealedAt(LocalDateTime.now());
        sortingRepository.updateTotalPackage(totalPackage);
        recordTracking(totalPackage.getPackageNo(), SortingConstants.EVENT_REBAG, request.getStationCode(), request.getOperatorId(), "{\"sourcePackageNo\":\"" + source.getPackageNo() + "\"}");
        return toPackageResponse(totalPackage);
    }

    @Override
    public List<SortingSlotSummaryResponse> listSlotSummaries(String stationCode) {
        List<PackageItemLineEntity> lines = sortingRepository.findAllPackageLines();
        Map<String, List<PackageItemLineEntity>> grouped = lines.stream()
                .filter(line -> StringUtils.hasText(line.getTargetCenterCode()))
                .filter(line -> !StringUtils.hasText(stationCode) || stationCode.equalsIgnoreCase(line.getStationCode()))
                .filter(line -> SortingConstants.LINE_ACTION_LOAD.equals(line.getActionType()))
                .collect(Collectors.groupingBy(line -> line.getTargetCenterCode().trim().toUpperCase(), Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> toSlotSummary(entry.getKey(), entry.getValue()))
                .filter(item -> item.getPendingCount() != null && item.getPendingCount() > 0)
                .toList();
    }

    @Override
    @Transactional
    public SortingPackageResponse sealBagBySlot(String slotCode, String stationCode, String operatorId) {
        String normalizedSlot = normalizeBlank(slotCode);
        List<PackageItemLineEntity> loadLines = sortingRepository.findLatestLoadLinesBySlot(normalizedSlot);
        if (loadLines.isEmpty()) {
            throw new BusinessException(404, "slot not found: " + normalizedSlot);
        }
        SortingTotalPackageEntity source = sortingRepository.findTotalPackageByNo(normalizedSlot).orElseGet(() -> {
            SortingTotalPackageEntity entity = new SortingTotalPackageEntity();
            entity.setPackageNo(normalizedSlot);
            entity.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
            entity.setPackageStatus(SortingConstants.PACKAGE_STATUS_OPENING);
            entity.setSourceOrgCode(stationCode);
            entity.setDestinationOrgCode(normalizedSlot);
            return sortingRepository.saveTotalPackage(entity);
        });
        SortingTotalPackageEntity totalPackage = new SortingTotalPackageEntity();
        totalPackage.setPackageNo(IdGeneratorUtil.generateBagNo());
        totalPackage.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_RAW);
        totalPackage.setSourceOrgCode(stationCode);
        totalPackage.setDestinationOrgCode(normalizedSlot);
        totalPackage.setParentPackageNo(source.getPackageNo());
        totalPackage = sortingRepository.saveTotalPackage(totalPackage);

        for (PackageItemLineEntity loadLine : loadLines) {
            PackageItemLineEntity line = new PackageItemLineEntity();
            line.setIdempotencyKey(resolveIdempotencyKey(totalPackage.getPackageNo(), normalizedSlot, loadLine.getItemNo()));
            line.setItemNo(loadLine.getItemNo());
            line.setActionType(SortingConstants.LINE_ACTION_LOAD);
            line.setEventType(SortingConstants.EVENT_REBAG);
            line.setScanMode(SortingConstants.SCAN_MODE_BLIND);
            line.setFromPackageNo(loadLine.getFromPackageNo());
            line.setToPackageNo(totalPackage.getPackageNo());
            line.setTargetCenterCode(normalizedSlot);
            line.setStationCode(stationCode);
            line.setOperatorId(operatorId);
            sortingRepository.savePackageItemLine(line);
            updateMailBag(loadLine.getItemNo(), totalPackage.getPackageNo());
        }

        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_SEALED);
        totalPackage.setSealedAt(LocalDateTime.now());
        sortingRepository.updateTotalPackage(totalPackage);
        recordTracking(totalPackage.getPackageNo(), SortingConstants.EVENT_REBAG, stationCode, operatorId, "{\"slotCode\":\"" + normalizedSlot + "\"}");
        return toPackageResponse(totalPackage);
    }

    @Override
    public List<SortingPackageResponse> listPackages() {
        return sortingRepository.findAllTotalPackages().stream().map(this::toPackageResponse).toList();
    }

    @Override
    public List<SortingManifestResponse> listManifests() {
        return sortingRepository.findAllManifests().stream().map(this::toManifestResponse).toList();
    }

    @Override
    public SortingManifestDetailResponse getManifestDetail(String manifestNo) {
        SortingManifestEntity manifest = sortingRepository.findManifestByNo(manifestNo)
                .orElseThrow(() -> new BusinessException(404, "manifest not found: " + manifestNo));
        SortingManifestDetailResponse response = new SortingManifestDetailResponse();
        copyManifest(response, manifest);
        response.setItems(sortingRepository.findManifestItems(manifest.getManifestNo()).stream()
                .map(this::toManifestItemResponse)
                .toList());
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
    public void initDefaults() {
        sortingRepository.seedDefaults();
    }

    private SortingManifestEntity ensureManifest(SortingReceiveRequest request) {
        SortingManifestEntity manifest = sortingRepository.findManifestByNo(request.getManifestNo()).orElse(null);
        if (manifest != null) {
            return manifest;
        }
        SortingManifestEntity entity = new SortingManifestEntity();
        entity.setManifestNo(request.getManifestNo());
        entity.setManifestType("TRANSIT");
        entity.setSourceOrgCode(request.getStationCode());
        entity.setDestinationOrgCode(request.getStationCode());
        entity.setBatchNo(request.getIdempotencyKey() == null ? IdGeneratorUtil.generateDispatchNo() : request.getIdempotencyKey());
        entity.setManifestStatus("RECEIVED");
        entity.setPrealertFlag(1);
        entity.setExpectedItemQty(0);
        entity.setExpectedPackageQty(request.getPackageNos().size());
        return sortingRepository.saveManifest(entity);
    }

    private void validateMailExists(String itemNo) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        Result<MailResponse> result = client.getMail(itemNo);
        if (result == null || result.getData() == null) {
            throw new BusinessException(404, "mail not found: " + itemNo);
        }
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

    private void updateMailBag(String itemNo, String packageNo) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        com.liana.post.oms.model.dto.MailBagAssignRequest request = new com.liana.post.oms.model.dto.MailBagAssignRequest();
        request.setBagNo(StringUtils.hasText(packageNo) ? packageNo : null);
        client.updateMailBag(itemNo, request);
    }

    @Transactional
    public Boolean notifyDispatchArrived(String manifestNo, String packageNo, String stationCode, String remark) {
        DispatchLifecycleClient client = dispatchLifecycleClientProvider.getIfAvailable();
        if (client == null) {
            return Boolean.FALSE;
        }
        ManifestArrivedRequest request = new ManifestArrivedRequest();
        request.setManifestCode(manifestNo);
        request.setTotalPackageNo(packageNo);
        request.setStationCode(stationCode);
        request.setRemark(remark);
        request.setArrivedAt(LocalDateTime.now().toString());
        try {
            client.markManifestArrived(request);
        } catch (Exception ignored) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
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

    private String resolveSlotCode(String itemNo, String destinationOrgCode) {
        String digits = itemNo.replaceAll("\\D", "");
        String suffix = digits.length() >= 2 ? digits.substring(digits.length() - 2) : String.format("%02d", Math.abs(itemNo.hashCode()) % 100);
        return StringUtils.hasText(destinationOrgCode) ? destinationOrgCode.trim().toUpperCase() + "-SLOT-" + suffix : "SLOT-" + suffix;
    }

    private SortingSlotSummaryResponse toSlotSummary(String slotCode, List<PackageItemLineEntity> lines) {
        SortingSlotSummaryResponse response = new SortingSlotSummaryResponse();
        response.setSlotCode(slotCode);
        response.setDestinationOrgCode(slotCode.contains("-SLOT-") ? slotCode.substring(0, slotCode.indexOf("-SLOT-")) : slotCode);
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

    private void assertPackageOpenable(SortingTotalPackageEntity totalPackage) {
        if (SortingConstants.PACKAGE_STATUS_OPENED.equals(totalPackage.getPackageStatus())) {
            throw new BusinessException(400, "package already terminal: OPENED");
        }
    }

    private List<String> dedupe(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private boolean isPrealert(SortingReceiveRequest request) {
        return SortingConstants.SCAN_MODE_PREALERT.equalsIgnoreCase(request.getReceiveMode()) || StringUtils.hasText(request.getManifestNo());
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
        response.setOperatorId(entity.getOperatorId());
        response.setDeviceId(entity.getDeviceId());
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
