package com.liana.post.dispatch.service.impl;

import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
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
        entity.setTransportMode(request.getTransportMode());
        entity.setPriorityLevel(request.getPriorityLevel());
        entity.setEnabled(1);
        return dispatchRepository.saveRouteRule(entity);
    }

    @Override
    @Transactional
    public DispatchBagResponse createDispatchBag(DispatchBagCreateRequest request) {
        List<String> mailNos = request.getMailNoList();
        if (mailNos == null || mailNos.isEmpty()) {
            throw new BusinessException(400, "mailNoList cannot be empty");
        }
        List<MailDispatchCandidateResponse> candidates = loadCandidates(request.getOriginFacilityCode(), request.getDestinationFacilityCode(), request.getMailTypeCode());
        validateCandidates(candidates, mailNos);

        String destinationFacilityCode = resolveDestinationFacilityCode(request.getDestinationFacilityCode(), candidates);
        RouteRuleEntity route = resolveRoute(request.getRouteCode(), request.getOriginFacilityCode(), destinationFacilityCode);

        DispatchBagEntity entity = new DispatchBagEntity();
        entity.setOriginFacilityCode(request.getOriginFacilityCode());
        entity.setDestinationFacilityCode(destinationFacilityCode);
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
        createDispatchBatchAuto(entity);
        syncManifestToSorting(bag.getBagNo(), bag.getRouteCode(), bag.getTotalWeightGrams(),
                request.getOriginFacilityCode(), request.getDestinationFacilityCode(), mailNos);
        return bag;
    }

    @Override
    @Transactional
    public DispatchBatchResponse createDispatchBatch(DispatchBatchCreateRequest request) {
        DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found"));
        DispatchBatchEntity entity = new DispatchBatchEntity();
        entity.setBagNo(bag.getBagNo());
        entity.setRouteCode(request.getRouteCode() == null || request.getRouteCode().isBlank() ? bag.getRouteCode() : request.getRouteCode().trim());
        entity.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        entity = dispatchRepository.saveDispatchBatch(entity);
        return toBatchResponse(entity);
    }

    @Override
    @Transactional
    public DispatchBatchResponse approveDispatchBatch(String batchNo, DispatchBatchApproveRequest request) {
        DispatchBatchEntity batch = dispatchRepository.findDispatchBatchByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(404, "dispatch batch not found"));
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

        batch.setStatus(DispatchConstants.BATCH_STATUS_HANDED_OFF);
        batch.setUpdatedAt(LocalDateTime.now());
        dispatchRepository.updateDispatchBatch(batch);

        bag.setStatus(DispatchConstants.BAG_STATUS_DISPATCHED);
        bag.setUpdatedAt(LocalDateTime.now());
        dispatchRepository.updateDispatchBag(bag);

        syncManifestToSorting(bag.getBagNo(), bag.getRouteCode(), bag.getTotalWeightGrams(),
                bag.getOriginFacilityCode(), request.getToFacilityCode(), bag.getMailNoListAsList());

        return toHandoffResponse(entity);
    }

    @Override
    @Transactional
    public Boolean markManifestArrived(ManifestArrivedRequest request) {
        if (request == null || request.getManifestCode() == null || request.getManifestCode().isBlank()) {
            throw new BusinessException(400, "manifestCode cannot be blank");
        }
        DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(request.getTotalPackageNo())
                .orElse(null);
        if (bag != null) {
            bag.setStatus(DispatchConstants.BAG_STATUS_DISPATCHED);
            bag.setUpdatedAt(LocalDateTime.now());
            dispatchRepository.updateDispatchBag(bag);
        }
        return Boolean.TRUE;
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

    private void syncManifestToSorting(String bagNo, String routeCode, Integer totalWeightGrams,
                                       String sourceFacilityCode, String destinationFacilityCode, List<String> mailNos) {
        SortingManifestClient client = sortingClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("sorting manifest client unavailable, manifest sync skipped. bagNo={}", bagNo);
            return;
        }
        ManifestDTO request = new ManifestDTO();
        request.setManifestCode(bagNo);
        request.setOriginNode(sourceFacilityCode);
        request.setTargetNode(destinationFacilityCode);
        request.setTotalWeight(java.math.BigDecimal.valueOf(totalWeightGrams == null ? 0 : totalWeightGrams));
        List<TotalPackageDTO> packages = new java.util.ArrayList<>();
        TotalPackageDTO totalPackage = new TotalPackageDTO();
        totalPackage.setPackageNo(bagNo);
        totalPackage.setPackageLevel("TRANSIT");
        totalPackage.setRouteCode(routeCode);
        totalPackage.setPackageWeight(java.math.BigDecimal.valueOf(totalWeightGrams == null ? 0 : totalWeightGrams));
        List<TotalPackageDTO.ManifestItemDTO> items = new java.util.ArrayList<>();
        for (String mailNo : mailNos) {
            TotalPackageDTO.ManifestItemDTO item = new TotalPackageDTO.ManifestItemDTO();
            item.setItemNo(mailNo);
            item.setSeqNo(items.size() + 1);
            item.setRouteCode(routeCode);
            item.setWeight(java.math.BigDecimal.ONE);
            items.add(item);
        }
        totalPackage.setItems(items);
        packages.add(totalPackage);
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

    private void createDispatchBatchAuto(DispatchBagEntity bag) {
        if (bag == null || bag.getBagNo() == null || bag.getBagNo().isBlank()) {
            return;
        }
        if (dispatchRepository.findDispatchBatchByBagNo(bag.getBagNo()).isPresent()) {
            return;
        }
        DispatchBatchEntity batch = new DispatchBatchEntity();
        batch.setBagNo(bag.getBagNo());
        batch.setRouteCode(bag.getRouteCode());
        batch.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        dispatchRepository.saveDispatchBatch(batch);
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

    private List<MailDispatchCandidateResponse> loadCandidates(String originFacilityCode, String destinationFacilityCode, String mailTypeCode) {
        OmsClient omsClient = omsClientProvider.getIfAvailable();
        if (omsClient == null) {
            throw new BusinessException(503, "oms client unavailable");
        }
        Result<List<MailDispatchCandidateResponse>> result = omsClient.listDispatchCandidates(buildCandidateQuery(originFacilityCode, destinationFacilityCode, mailTypeCode));
        return result == null || result.getData() == null ? List.of() : result.getData();
    }

    private MailDispatchCandidateQueryRequest buildCandidateQuery(String originFacilityCode, String destinationFacilityCode, String mailTypeCode) {
        MailDispatchCandidateQueryRequest query = new MailDispatchCandidateQueryRequest();
        query.setCurrentFacilityCode(originFacilityCode);
        if (destinationFacilityCode != null && !destinationFacilityCode.isBlank()) {
            query.setDestFacilityCode(destinationFacilityCode);
        }
        query.setMailTypeCode(mailTypeCode);
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

    private RouteRuleEntity resolveRoute(String routeCode, String sourceFacilityCode, String targetFacilityCode) {
        if (routeCode != null && !routeCode.isBlank()) {
            return dispatchRepository.findRouteRuleByCode(routeCode.trim()).orElse(null);
        }
        if (targetFacilityCode == null || targetFacilityCode.isBlank()) {
            return null;
        }
        return dispatchRepository.findBestRouteRule(sourceFacilityCode, targetFacilityCode).orElse(null);
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
        response.setStatus(entity.getStatus());
        response.setMailTypeCode(entity.getMailTypeCode());
        response.setMailNoList(entity.getMailNoListAsList());
        response.setMailCount(entity.getMailCount());
        response.setTotalWeightGrams(entity.getTotalWeightGrams());
        return response;
    }

    private DispatchBatchResponse toBatchResponse(DispatchBatchEntity entity) {
        DispatchBatchResponse response = new DispatchBatchResponse();
        response.setId(entity.getId());
        response.setBatchNo(entity.getBatchNo());
        response.setBagNo(entity.getBagNo());
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
