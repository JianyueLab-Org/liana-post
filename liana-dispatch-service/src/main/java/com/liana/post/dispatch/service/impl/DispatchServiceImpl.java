package com.liana.post.dispatch.service.impl;

import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.sorting.ManifestArrivedRequest;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.dispatch.client.OmsClient;
import com.liana.post.dispatch.client.SortingManifestClient;
import com.liana.post.dispatch.constant.DispatchConstants;
import com.liana.post.dispatch.model.dto.DispatchBagCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBagResponse;
import com.liana.post.dispatch.model.dto.DispatchBatchApproveRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchResponse;
import com.liana.post.dispatch.model.dto.DispatchMailSyncRequest;
import com.liana.post.dispatch.model.dto.RouteDecisionRequest;
import com.liana.post.dispatch.model.dto.RouteDecisionResponse;
import com.liana.post.dispatch.model.dto.HandoffCreateRequest;
import com.liana.post.dispatch.model.dto.HandoffRecordResponse;
import com.liana.post.dispatch.model.dto.RouteRuleCreateRequest;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.model.entity.HandoffRecordEntity;
import com.liana.post.dispatch.model.entity.RouteRuleEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
import com.liana.post.dispatch.service.DispatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DispatchServiceImpl implements DispatchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private final DispatchRepository dispatchRepository;
    private final ObjectProvider<OmsClient> omsClientProvider;
    private final HttpServletRequest httpServletRequest;
    private final ObjectProvider<SortingManifestClient> sortingClientProvider;
    private final String sortingServiceUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DispatchServiceImpl(DispatchRepository dispatchRepository,
                               ObjectProvider<OmsClient> omsClientProvider,
                               HttpServletRequest httpServletRequest,
                               ObjectProvider<SortingManifestClient> sortingClientProvider,
                               @Value("${sorting.service.url:http://localhost:8087}") String sortingServiceUrl) {
        this.dispatchRepository = dispatchRepository;
        this.omsClientProvider = omsClientProvider;
        this.httpServletRequest = httpServletRequest;
        this.sortingClientProvider = sortingClientProvider;
        this.sortingServiceUrl = sortingServiceUrl;
    }

    @Override
    @Transactional
    public RouteRuleEntity createRouteRule(RouteRuleCreateRequest request) {
        RouteRuleEntity entity = new RouteRuleEntity();
        entity.setRuleCode(request.getRuleCode());
        entity.setSourceFacilityCode(request.getSourceFacilityCode());
        entity.setTargetFacilityCode(request.getTargetFacilityCode());
        entity.setRouteScope(normalizeRouteScope(request.getRouteScope()));
        entity.setDestCountryCode(normalizeOptional(request.getDestCountryCode()));
        entity.setExportFacilityCode(normalizeOptional(request.getExportFacilityCode()));
        entity.setTransportMode(request.getTransportMode());
        entity.setPriorityLevel(request.getPriorityLevel());
        entity.setEnabled(1);
        return dispatchRepository.saveRouteRule(entity);
    }

    @Override
    public RouteDecisionResponse resolveRouteDecision(RouteDecisionRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request cannot be null");
        }
        String origin = normalizeRequired(request.getOriginFacilityCode(), "originFacilityCode");
        String destCountryCode = normalizeRequired(request.getDestCountryCode(), "destCountryCode");
        String actionMode = normalizeActionMode(request.getActionMode());
        boolean international = !isDomesticCountry(destCountryCode);
        String preferredExportFacilityCode = normalizeOptional(request.getManualExportFacilityCode());
        RouteRuleEntity route = null;
        if (request.getCurrentRouteCode() != null && !request.getCurrentRouteCode().isBlank()) {
            route = dispatchRepository.findRouteRuleByCode(request.getCurrentRouteCode().trim()).orElse(null);
        }
        if (route == null && international) {
            route = dispatchRepository.findBestRouteRule(origin, preferredExportFacilityCode, "INTERNATIONAL", destCountryCode)
                    .orElse(dispatchRepository.findBestRouteRule(origin, null, "INTERNATIONAL", destCountryCode).orElse(null));
        }

        RouteDecisionResponse response = new RouteDecisionResponse();
        response.setSourceFacilityCode(origin);
        response.setMailTypeCode(normalizeRequired(request.getMailTypeCode(), "mailTypeCode"));
        response.setDestCountryCode(destCountryCode);
        response.setRouteScope(international ? "INTERNATIONAL" : "DOMESTIC");
        response.setTransportMode(route == null ? null : route.getTransportMode());
        response.setRouteCode(route == null ? null : route.getRuleCode());
        response.setTargetFacilityCode(route == null ? null : resolveRouteTarget(route));
        response.setExportFacilityCode(route == null ? preferredExportFacilityCode : route.getExportFacilityCode());

        if ("RETURN".equals(actionMode)) {
            response.setDecision("RETURN_REQUIRED");
            response.setReason("manual return requested");
            return response;
        }
        if (route == null) {
            if ("MANUAL".equals(actionMode)) {
                response.setDecision("MANUAL_EXPORT_SELECTION");
                response.setReason("no matching route rule found");
                return response;
            }
            response.setDecision("RETURN_REQUIRED");
            response.setReason("no matching route rule found");
            return response;
        }

        response.setDecision("ROUTE_RESOLVED");
        response.setReason("matched route rule");
        return response;
    }

    @Override
    @Transactional
    public DispatchBagResponse createDispatchBag(DispatchBagCreateRequest request) {
        List<String> mailNos = request.getMailNoList();
        if (mailNos == null || mailNos.isEmpty()) {
            throw new BusinessException(400, "mailNoList cannot be empty");
        }
        List<MailDispatchCandidateResponse> candidates = loadCandidates(request.getOriginFacilityCode(), request.getDestinationFacilityCode(), request.getMailTypeCode(), request.getMailScope(), request.getDestCountryCode());
        validateCandidates(candidates, mailNos);

        String effectiveCountryCode = resolveEffectiveDestCountryCode(request.getDestCountryCode(), candidates);
        String destinationFacilityCode = resolveDestinationFacilityCode(request.getDestinationFacilityCode(), candidates);
        boolean international = effectiveCountryCode != null && !isDomesticCountry(effectiveCountryCode);
        RouteRuleEntity route = resolveRoute(request.getRouteCode(), request.getOriginFacilityCode(), destinationFacilityCode,
                international ? effectiveCountryCode : null, international);
        // Post offices can form a new total package before sorting/export route is known.
        // If a route exists, keep it on the bag; otherwise downstream sorting resolves it later.
        String resolvedTargetFacilityCode = resolveRouteTarget(route, destinationFacilityCode);
        if (international && route != null && route.getExportFacilityCode() != null && !route.getExportFacilityCode().isBlank()) {
            resolvedTargetFacilityCode = route.getExportFacilityCode();
        }

        DispatchBagEntity entity = new DispatchBagEntity();
        entity.setOriginFacilityCode(request.getOriginFacilityCode());
        entity.setDestinationFacilityCode(resolvedTargetFacilityCode);
        entity.setRouteCode(route == null ? request.getRouteCode() : route.getRuleCode());
        entity.setMailTypeCode(request.getMailTypeCode());
        entity.setMailNoListAsList(mailNos);
        entity.setMailCount(mailNos.size());
        entity.setTotalWeightGrams(calculateWeight(mailNos.size()));
        entity.setStatus(DispatchConstants.BAG_STATUS_CREATED);
        entity = dispatchRepository.saveDispatchBag(entity);

        DispatchMailSyncRequest syncRequest = new DispatchMailSyncRequest();
        syncRequest.setBagNo(entity.getBagNo());
        syncRequest.setCurrentFacilityCode(request.getOriginFacilityCode());
        syncRequest.setMailNoList(mailNos);
        DispatchBagResponse bag = syncMailBag(syncRequest);
        return bag;
    }

    @Override
    @Transactional
    public DispatchBatchResponse createDispatchBatch(DispatchBatchCreateRequest request) {
        List<String> requestedBagNos = request.getBagNos() == null || request.getBagNos().isEmpty()
                ? (request.getBagNo() == null || request.getBagNo().isBlank() ? List.of() : List.of(request.getBagNo()))
                : request.getBagNos();
        List<DispatchBagEntity> bags = requestedBagNos.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim).map(String::toUpperCase).distinct()
                .map(bagNo -> dispatchRepository.findDispatchBagByBagNo(bagNo)
                        .orElseThrow(() -> new BusinessException(404, "dispatch bag not found: " + bagNo)))
                .toList();
        if (bags.isEmpty()) {
            throw new BusinessException(400, "bagNo or bagNos is required");
        }
        DispatchBagEntity bag = bags.get(0);
        String routeCode = request.getRouteCode() == null || request.getRouteCode().isBlank()
                ? bag.getRouteCode() : request.getRouteCode().trim();
        if (!java.util.Objects.equals(routeCode, bag.getRouteCode())) {
            throw new BusinessException(400, "dispatch batch route must match the bag route");
        }
        for (DispatchBagEntity candidate : bags) {
            if (!java.util.Objects.equals(candidate.getOriginFacilityCode(), bag.getOriginFacilityCode())
                    || !java.util.Objects.equals(candidate.getDestinationFacilityCode(), bag.getDestinationFacilityCode())
                    || !java.util.Objects.equals(candidate.getRouteCode(), bag.getRouteCode())) {
                throw new BusinessException(400, "all bags in a dispatch batch must share origin, destination, and route");
            }
            if (DispatchConstants.BAG_STATUS_DISPATCHED.equals(candidate.getStatus())
                    || DispatchConstants.BAG_STATUS_ARRIVED.equals(candidate.getStatus())) {
                throw new BusinessException(409, "dispatched bags cannot be added to a new batch");
            }
        }
        DispatchBatchEntity entity = new DispatchBatchEntity();
        entity.setBagNo(bag.getBagNo());
        entity.setRouteCode(routeCode);
        entity.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        entity = dispatchRepository.saveDispatchBatch(entity);
        List<String> bagNos = bags.stream().map(DispatchBagEntity::getBagNo).toList();
        dispatchRepository.replaceBatchBags(entity.getBatchNo(), bagNos);
        entity.setBagNos(bagNos);
        syncManifestToSorting("M" + entity.getBatchNo(), routeCode, bag.getOriginFacilityCode(),
                bag.getDestinationFacilityCode(), bags);
        return toBatchResponse(entity);
    }

    @Override
    @Transactional
    public DispatchBatchResponse approveDispatchBatch(String batchNo, DispatchBatchApproveRequest request) {
        DispatchBatchEntity batch = dispatchRepository.findDispatchBatchByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(404, "dispatch batch not found"));
        if (!DispatchConstants.BATCH_STATUS_PENDING.equals(batch.getStatus())) {
            throw new BusinessException(409, "only pending dispatch batches can be approved");
        }
        batch.setStatus(DispatchConstants.BATCH_STATUS_APPROVED);
        batch.setApprovedBy(request.getApprovedBy());
        batch.setApprovedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        return toBatchResponse(dispatchRepository.updateDispatchBatch(batch));
    }

    @Override
    @Transactional
    public HandoffRecordResponse createHandoffRecord(HandoffCreateRequest request) {
        String currentFacilityCode = resolveCurrentFacilityCode();
        if (currentFacilityCode == null || currentFacilityCode.isBlank()) {
            throw new BusinessException(401, "current facility cannot be resolved from token");
        }

        DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found"));
        if (!currentFacilityCode.equalsIgnoreCase(bag.getOriginFacilityCode())) {
            throw new BusinessException(403, "current facility does not own this dispatch bag");
        }
        if (!java.util.Objects.equals(request.getToFacilityCode(), bag.getDestinationFacilityCode())) {
            throw new BusinessException(400, "handoff destination must match the bag destination");
        }
        if (DispatchConstants.BAG_STATUS_DISPATCHED.equals(bag.getStatus())
                || dispatchRepository.findHandoffRecordByBagNo(bag.getBagNo()).isPresent()) {
            throw new BusinessException(409, "dispatch bag has already been handed off");
        }
        DispatchBatchEntity batch = dispatchRepository.findDispatchBatchByBagNo(bag.getBagNo())
                .orElseGet(() -> {
                    DispatchBatchEntity created = new DispatchBatchEntity();
                    created.setBagNo(bag.getBagNo());
                    created.setRouteCode(bag.getRouteCode());
                    created.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
                    return dispatchRepository.saveDispatchBatch(created);
                });

        if (!DispatchConstants.BATCH_STATUS_APPROVED.equals(batch.getStatus())) {
            batch.setStatus(DispatchConstants.BATCH_STATUS_APPROVED);
            batch.setUpdatedAt(LocalDateTime.now());
            dispatchRepository.updateDispatchBatch(batch);
        }

        HandoffRecordEntity entity = new HandoffRecordEntity();
        entity.setBagNo(bag.getBagNo());
        entity.setBatchNo(batch.getBatchNo());
        entity.setFromFacilityCode(currentFacilityCode);
        entity.setToFacilityCode(request.getToFacilityCode());
        entity.setReceiverId(request.getReceiverId());
        entity.setHandoffTime(LocalDateTime.now());
        entity.setStatus(DispatchConstants.HANDAOFF_STATUS_COMPLETED);
        entity = dispatchRepository.saveHandoffRecord(entity);

        List<String> batchBagNos = getBatchBagNos(batch);
        boolean allBagsHandedOff = batchBagNos.stream()
                .allMatch(bagNo -> dispatchRepository.findHandoffRecordByBagNo(bagNo).isPresent());
        batch.setStatus(allBagsHandedOff
                ? DispatchConstants.BATCH_STATUS_HANDED_OFF
                : DispatchConstants.BATCH_STATUS_APPROVED);
        batch.setUpdatedAt(LocalDateTime.now());
        dispatchRepository.updateDispatchBatch(batch);

        bag.setStatus(DispatchConstants.BAG_STATUS_DISPATCHED);
        bag.setUpdatedAt(LocalDateTime.now());
        dispatchRepository.updateDispatchBag(bag);

        List<DispatchBagEntity> batchBags = batchBagNos.stream()
                .map(bagNo -> dispatchRepository.findDispatchBagByBagNo(bagNo).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (batchBags.size() != batchBagNos.size() || batchBags.stream().anyMatch(item ->
                !currentFacilityCode.equalsIgnoreCase(item.getOriginFacilityCode())
                        || !java.util.Objects.equals(request.getToFacilityCode(), item.getDestinationFacilityCode()))) {
            throw new BusinessException(409, "all bags in a batch must be handed off together along their planned route");
        }
        syncManifestToSorting("M" + batch.getBatchNo(), batch.getRouteCode(), bag.getOriginFacilityCode(),
                request.getToFacilityCode(), batchBags);

        return toHandoffResponse(entity);
    }

    @Override
    @Transactional
    public Boolean markManifestArrived(ManifestArrivedRequest request) {
        if (request == null || request.getManifestCode() == null || request.getManifestCode().isBlank()
                || request.getTotalPackageNo() == null || request.getTotalPackageNo().isBlank()) {
            throw new BusinessException(400, "manifestCode and totalPackageNo cannot be blank");
        }
        DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(request.getTotalPackageNo())
                .orElse(null);
        if (bag == null) {
            return Boolean.TRUE;
        }
        DispatchBatchEntity batch = dispatchRepository.findDispatchBatchByBagNo(bag.getBagNo()).orElse(null);
        if (batch == null) {
            return Boolean.TRUE;
        }
        String expectedManifestCode = "M" + batch.getBatchNo();
        if (!expectedManifestCode.equalsIgnoreCase(request.getManifestCode().trim())) {
            throw new BusinessException(409, "manifest does not belong to the dispatch bag batch");
        }
        if (!getBatchBagNos(batch).stream().anyMatch(bag.getBagNo()::equalsIgnoreCase)) {
            throw new BusinessException(409, "dispatch bag is not linked to its batch");
        }
        bag.setStatus(DispatchConstants.BAG_STATUS_ARRIVED);
        bag.setUpdatedAt(LocalDateTime.now());
        dispatchRepository.updateDispatchBag(bag);
        return Boolean.TRUE;
    }

    private List<String> getBatchBagNos(DispatchBatchEntity batch) {
        if (batch == null) {
            return List.of();
        }
        if (batch.getBagNos() != null && !batch.getBagNos().isEmpty()) {
            return batch.getBagNos();
        }
        return batch.getBagNo() == null || batch.getBagNo().isBlank()
                ? List.of() : List.of(batch.getBagNo());
    }

    @Override
    @Transactional
    public DispatchBagResponse syncMailBag(DispatchMailSyncRequest request) {
        DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found"));
        if (request.getMailNoList() == null || request.getMailNoList().isEmpty()) {
            throw new BusinessException(400, "mailNoList cannot be empty");
        }
        OmsClient omsClient = omsClientProvider.getIfAvailable();
        if (omsClient == null) {
            throw new BusinessException(503, "oms client unavailable");
        }
        int updated = dispatchRepository.assignMailBagToWaybillNos(request.getMailNoList(), bag.getBagNo(), request.getCurrentFacilityCode());
        if (updated == 0) {
            throw new BusinessException(400, "no mail updated");
        }
        MailBagSyncRequest updateRequest = new MailBagSyncRequest();
        updateRequest.setBagNo(bag.getBagNo());
        updateRequest.setCurrentFacilityCode(request.getCurrentFacilityCode());
        updateRequest.setWaybillNos(request.getMailNoList());
        omsClient.updateMailStatuses(updateRequest);
        bag.setMailNoListAsList(request.getMailNoList());
        bag.setMailCount(request.getMailNoList().size());
        bag.setStatus(DispatchConstants.BAG_STATUS_REVIEWED);
        bag.setUpdatedAt(LocalDateTime.now());
        return toBagResponse(dispatchRepository.updateDispatchBag(bag));
    }

    private void syncManifestToSorting(String manifestNo, String routeCode,
                                       String sourceFacilityCode, String destinationFacilityCode, List<DispatchBagEntity> bags) {
        String bagNo = bags == null || bags.isEmpty() ? null : bags.get(0).getBagNo();
        SortingManifestClient client = sortingClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("sorting manifest client unavailable, manifest sync skipped. bagNo={}", bagNo);
            return;
        }
        ManifestDTO request = new ManifestDTO();
        request.setManifestCode(manifestNo);
        request.setOriginNode(sourceFacilityCode);
        request.setTargetNode(destinationFacilityCode);
        request.setTotalWeight(java.math.BigDecimal.valueOf(bags.stream()
                .map(DispatchBagEntity::getTotalWeightGrams).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum()));
        List<TotalPackageDTO> packages = new java.util.ArrayList<>();
        for (DispatchBagEntity bag : bags) {
            TotalPackageDTO totalPackage = new TotalPackageDTO();
            totalPackage.setPackageNo(bag.getBagNo());
            totalPackage.setPackageLevel("TRANSIT");
            totalPackage.setRouteCode(routeCode);
            totalPackage.setPackageWeight(java.math.BigDecimal.valueOf(bag.getTotalWeightGrams() == null ? 0 : bag.getTotalWeightGrams()));
            List<TotalPackageDTO.ManifestItemDTO> items = new java.util.ArrayList<>();
            for (String mailNo : bag.getMailNoListAsList() == null ? List.<String>of() : bag.getMailNoListAsList()) {
                TotalPackageDTO.ManifestItemDTO item = new TotalPackageDTO.ManifestItemDTO();
                item.setItemNo(mailNo);
                item.setSeqNo(items.size() + 1);
                item.setRouteCode(routeCode);
                item.setWeight(java.math.BigDecimal.ONE);
                items.add(item);
            }
            totalPackage.setItems(items);
            packages.add(totalPackage);
        }
        request.setTotalPackages(packages);
        try {
            Result<Boolean> result = client.syncManifest(request);
            if (result == null || !Boolean.TRUE.equals(result.getData())) {
                log.warn("sorting manifest sync returned non-success, bagNo={}, manifestCode={}, result={}",
                        bagNo, request.getManifestCode(), result);
                fallbackSyncManifest(request);
            }
        } catch (Exception ex) {
            log.warn("sorting manifest sync failed, bagNo={}, manifestCode={}", bagNo, request.getManifestCode(), ex);
            fallbackSyncManifest(request);
        }
    }

    private void fallbackSyncManifest(ManifestDTO request) {
        if (sortingServiceUrl == null || sortingServiceUrl.isBlank()) {
            return;
        }
        try {
            String endpoint = sortingServiceUrl.endsWith("/")
                    ? sortingServiceUrl + "api/v1/sorting/manifest/sync"
                    : sortingServiceUrl + "/api/v1/sorting/manifest/sync";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(com.alibaba.fastjson2.JSON.toJSONString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("sorting manifest fallback sync failed, status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("sorting manifest fallback sync exception, manifestCode={}", request.getManifestCode(), ex);
        }
    }

    private DispatchBatchEntity createDispatchBatchAuto(DispatchBagEntity bag) {
        if (bag == null || bag.getBagNo() == null || bag.getBagNo().isBlank()) {
            throw new BusinessException(400, "dispatch bag is required");
        }
        DispatchBatchEntity existing = dispatchRepository.findDispatchBatchByBagNo(bag.getBagNo()).orElse(null);
        if (existing != null) return existing;
        DispatchBatchEntity batch = new DispatchBatchEntity();
        batch.setBagNo(bag.getBagNo());
        batch.setRouteCode(bag.getRouteCode());
        batch.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        return dispatchRepository.saveDispatchBatch(batch);
    }

    @Override
    public DispatchBagResponse getDispatchBagById(Long id) {
        return toBagResponse(dispatchRepository.findDispatchBagById(id)
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found: " + id)));
    }

    @Override
    @Transactional
    public DispatchBagResponse linkTransportTask(Long id, DispatchTransportTaskLinkRequest request) {
        if (request == null || request.getTransportTaskCode() == null || request.getTransportTaskCode().isBlank()) {
            throw new BusinessException(400, "transportTaskCode is required");
        }
        DispatchBagEntity entity = dispatchRepository.linkTransportTask(id, request.getTransportTaskCode().trim());
        return toBagResponse(entity);
    }

    @Override
    public List<DispatchBagResponse> listDispatchBags() {
        return dispatchRepository.findAllDispatchBags().stream().map(this::toBagResponse).toList();
    }

    @Override
    public List<DispatchBatchResponse> listDispatchBatches() {
        return dispatchRepository.findAllDispatchBatches().stream().map(this::toBatchResponse).toList();
    }

    @Override
    public List<HandoffRecordResponse> listHandoffRecords() {
        return dispatchRepository.findAllHandoffRecords().stream().map(this::toHandoffResponse).toList();
    }

    @Override
    public DashboardSummaryResponse dashboardSummary(String facilityCode) {
        List<DispatchBagResponse> bags = listDispatchBags().stream()
                .filter(bag -> isAllFacility(facilityCode) || matchesFacility(bag.getOriginFacilityCode(), facilityCode) || matchesFacility(bag.getDestinationFacilityCode(), facilityCode))
                .toList();
        List<DispatchBatchResponse> batches = listDispatchBatches();
        List<HandoffRecordResponse> handoffs = listHandoffRecords().stream()
                .filter(handoff -> isAllFacility(facilityCode) || matchesFacility(handoff.getFromFacilityCode(), facilityCode) || matchesFacility(handoff.getToFacilityCode(), facilityCode))
                .toList();
        int mailCount = bags.stream()
                .map(DispatchBagResponse::getMailCount)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long dispatched = bags.stream().filter(bag -> DispatchConstants.BAG_STATUS_DISPATCHED.equals(bag.getStatus())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("调度数据");
        response.setScope(scopeLabel(facilityCode));
        response.addMetric("邮袋数", bags.size(), "dispatch_bag 表", "info")
                .addMetric("已发运", dispatched, "DISPATCHED", "success")
                .addMetric("批次数", batches.size(), "dispatch_batch 表", "neutral")
                .addMetric("邮件件数", mailCount, "邮袋 mail_count 汇总", "warning");
        response.addBreakdown("邮袋状态", countBy(bags.stream().map(DispatchBagResponse::getStatus).collect(Collectors.toList())));
        response.addBreakdown("交接状态", countBy(handoffs.stream().map(HandoffRecordResponse::getStatus).collect(Collectors.toList())));
        return response;
    }

    @Override
    public void initDefaults() {
        dispatchRepository.seedDefaults();
    }

    private void validateCandidates(List<MailDispatchCandidateResponse> candidates, List<String> mailNos) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(400, "no dispatch candidates found");
        }
        Set<String> candidateNos = new HashSet<>();
        for (MailDispatchCandidateResponse candidate : candidates) {
            candidateNos.add(candidate.getWaybillNo());
        }
        for (String mailNo : mailNos) {
            if (!candidateNos.contains(mailNo)) {
                throw new BusinessException(400, "mail not allowed for dispatch: " + mailNo);
            }
        }
    }

    private String resolveEffectiveDestCountryCode(String requestedCountryCode, List<MailDispatchCandidateResponse> candidates) {
        String normalizedRequested = normalizeOptional(requestedCountryCode);
        Set<String> effectiveCountries = new HashSet<>();
        for (MailDispatchCandidateResponse candidate : candidates) {
            effectiveCountries.add(normalizeCountryCode(candidate == null ? null : candidate.getDestCountryCode()));
        }
        if (effectiveCountries.size() > 1) {
            throw new BusinessException(400, "selected mail candidates have mixed destination countries");
        }
        String effectiveCountryCode = effectiveCountries.stream().findFirst().orElse(null);
        if (normalizedRequested != null) {
            String normalizedEffective = effectiveCountryCode == null ? "LN" : effectiveCountryCode;
            if (!normalizedRequested.equals(normalizedEffective)) {
                throw new BusinessException(400, "selected mail candidates do not match requested destination country");
            }
            return isDomesticCountry(normalizedRequested) ? null : normalizedRequested;
        }
        return isDomesticCountry(effectiveCountryCode) ? null : effectiveCountryCode;
    }

    private List<MailDispatchCandidateResponse> loadCandidates(String originFacilityCode, String destinationFacilityCode, String mailTypeCode, String mailScope, String destCountryCode) {
        OmsClient omsClient = omsClientProvider.getIfAvailable();
        if (omsClient == null) {
            throw new BusinessException(503, "oms client unavailable");
        }
        Result<List<MailDispatchCandidateResponse>> result = omsClient.listDispatchCandidates(buildCandidateQuery(originFacilityCode, destinationFacilityCode, mailTypeCode, mailScope, destCountryCode));
        return result == null || result.getData() == null ? List.of() : result.getData();
    }

    private MailDispatchCandidateQueryRequest buildCandidateQuery(String originFacilityCode, String destinationFacilityCode, String mailTypeCode, String mailScope, String destCountryCode) {
        MailDispatchCandidateQueryRequest query = new MailDispatchCandidateQueryRequest();
        query.setCurrentFacilityCode(originFacilityCode);
        if (destinationFacilityCode != null && !destinationFacilityCode.isBlank()) {
            query.setDestFacilityCode(destinationFacilityCode);
        }
        query.setMailTypeCode(mailTypeCode);
        query.setMailScope(normalizeOptional(mailScope));
        query.setDestCountryCode(normalizeOptional(destCountryCode));
        return query;
    }

    private String resolveDestinationFacilityCode(String requestedDestinationFacilityCode, List<MailDispatchCandidateResponse> candidates) {
        if (requestedDestinationFacilityCode != null && !requestedDestinationFacilityCode.isBlank()) {
            return requestedDestinationFacilityCode.trim();
        }
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        for (MailDispatchCandidateResponse candidate : candidates) {
            if (candidate.getDestFacilityCode() != null && !candidate.getDestFacilityCode().isBlank()) {
                return candidate.getDestFacilityCode();
            }
        }
        return "";
    }

    private RouteRuleEntity resolveRoute(String routeCode, String sourceFacilityCode, String targetFacilityCode, String destCountryCode, boolean international) {
        if (routeCode != null && !routeCode.isBlank()) {
            return dispatchRepository.findRouteRuleByCode(routeCode.trim()).orElse(null);
        }
        if (international) {
            return dispatchRepository.findBestRouteRule(sourceFacilityCode, targetFacilityCode, "INTERNATIONAL", destCountryCode)
                    .orElse(dispatchRepository.findBestRouteRule(sourceFacilityCode, targetFacilityCode, "INTERNATIONAL", null).orElse(null));
        }
        if (targetFacilityCode == null || targetFacilityCode.isBlank()) {
            return null;
        }
        return dispatchRepository.findBestRouteRule(sourceFacilityCode, targetFacilityCode, "DOMESTIC", null)
                .orElse(dispatchRepository.findBestRouteRule(sourceFacilityCode, targetFacilityCode).orElse(null));
    }

    private boolean isInternationalCandidate(MailDispatchCandidateResponse candidate) {
        return candidate != null && candidate.getDestCountryCode() != null && !candidate.getDestCountryCode().isBlank() && !"LN".equalsIgnoreCase(candidate.getDestCountryCode());
    }

    private boolean isDomesticCountry(String countryCode) {
        return countryCode == null || countryCode.isBlank() || "LN".equalsIgnoreCase(countryCode.trim());
    }

    private boolean isAllFacility(String facilityCode) {
        return facilityCode == null || facilityCode.isBlank();
    }

    private boolean matchesFacility(String actual, String facilityCode) {
        return actual != null && facilityCode != null && actual.equalsIgnoreCase(facilityCode.trim());
    }

    private String scopeLabel(String facilityCode) {
        return isAllFacility(facilityCode) ? "全部机构" : facilityCode.trim().toUpperCase();
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

    private String normalizeRouteScope(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.isBlank()) {
            return "DOMESTIC";
        }
        if (!"DOMESTIC".equals(normalized) && !"INTERNATIONAL".equals(normalized)) {
            throw new BusinessException(400, "unsupported route scope");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String normalizeCountryCode(String value) {
        return isDomesticCountry(value) ? "LN" : normalizeOptional(value);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, fieldName + " is required");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeActionMode(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.isBlank()) {
            return "AUTO";
        }
        if (!"AUTO".equals(normalized) && !"MANUAL".equals(normalized) && !"RETURN".equals(normalized)) {
            throw new BusinessException(400, "unsupported actionMode");
        }
        return normalized;
    }

    private String resolveRouteTarget(RouteRuleEntity route) {
        if (route == null) {
            return null;
        }
        if (route.getExportFacilityCode() != null && !route.getExportFacilityCode().isBlank()) {
            return route.getExportFacilityCode();
        }
        return route.getTargetFacilityCode();
    }

    private String resolveRouteTarget(RouteRuleEntity route, String fallbackTargetFacilityCode) {
        String target = resolveRouteTarget(route);
        if (target != null && !target.isBlank()) {
            return target;
        }
        return fallbackTargetFacilityCode;
    }

    private int calculateWeight(int mailCount) {
        return mailCount * 100;
    }

    private DispatchBagResponse toBagResponse(DispatchBagEntity entity) {
        DispatchBagResponse response = new DispatchBagResponse();
        response.setId(entity.getId());
        response.setDispatchBagId(entity.getId());
        response.setBagNo(entity.getBagNo());
        response.setOriginFacilityCode(entity.getOriginFacilityCode());
        response.setDestinationFacilityCode(entity.getDestinationFacilityCode());
        response.setRouteCode(entity.getRouteCode());
        response.setTransportTaskCode(entity.getTransportTaskCode());
        response.setStatus(effectiveBagStatus(entity));
        response.setMailTypeCode(entity.getMailTypeCode());
        response.setMailNoList(entity.getMailNoListAsList());
        response.setMailCount(entity.getMailCount());
        response.setTotalWeightGrams(entity.getTotalWeightGrams());
        return response;
    }

    private String effectiveBagStatus(DispatchBagEntity entity) {
        if (entity == null) {
            return null;
        }
        if (DispatchConstants.BAG_STATUS_DISPATCHED.equals(entity.getStatus())
                || DispatchConstants.BAG_STATUS_ARRIVED.equals(entity.getStatus())) {
            return entity.getStatus();
        }
        return dispatchRepository.findHandoffRecordByBagNo(entity.getBagNo()).isPresent()
                ? DispatchConstants.BAG_STATUS_DISPATCHED
                : entity.getStatus();
    }

    private DispatchBatchResponse toBatchResponse(DispatchBatchEntity entity) {
        DispatchBatchResponse response = new DispatchBatchResponse();
        response.setId(entity.getId());
        response.setBatchNo(entity.getBatchNo());
        response.setBagNo(entity.getBagNo());
        response.setBagNos(entity.getBagNos() == null ? List.of(entity.getBagNo()) : entity.getBagNos());
        response.setRouteCode(entity.getRouteCode());
        response.setStatus(entity.getStatus());
        response.setApprovedBy(entity.getApprovedBy());
        response.setApprovedAt(entity.getApprovedAt() == null ? null : entity.getApprovedAt().format(FORMATTER));
        return response;
    }

    private HandoffRecordResponse toHandoffResponse(HandoffRecordEntity entity) {
        HandoffRecordResponse response = new HandoffRecordResponse();
        response.setId(entity.getId());
        response.setHandoffNo(entity.getHandoffNo());
        response.setBagNo(entity.getBagNo());
        response.setFromFacilityCode(entity.getFromFacilityCode());
        response.setToFacilityCode(entity.getToFacilityCode());
        response.setHandoffTime(entity.getHandoffTime() == null ? null : entity.getHandoffTime().format(FORMATTER));
        response.setReceiverId(entity.getReceiverId());
        response.setStatus(entity.getStatus());
        return response;
    }

    private String resolveCurrentFacilityCode() {
        String authorization = httpServletRequest.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String token = authorization.replaceFirst("(?i)^Bearer\\s+", "");
        return JwtTokenUtil.getFacilityCodeFromToken(token);
    }
}
