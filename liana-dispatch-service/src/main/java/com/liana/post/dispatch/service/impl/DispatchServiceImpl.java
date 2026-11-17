package com.liana.post.dispatch.service.impl;

import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.sorting.ManifestArrivedRequest;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.constant.AuthConstants;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.dispatch.client.OmsClient;
import com.liana.post.dispatch.client.FacilityRouteClient;
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
import com.liana.post.dispatch.service.ManifestOutbox;
import com.liana.post.dispatch.service.BagPackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

@Service
public class DispatchServiceImpl implements DispatchService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DispatchRepository dispatchRepository;
    private final ObjectProvider<OmsClient> omsClientProvider;
    private final HttpServletRequest httpServletRequest;
    private final ManifestOutbox manifestOutbox;
    private final BagPackingService bagPacking;
    private final ObjectProvider<FacilityRouteClient> facilityRouteClientProvider;

    public DispatchServiceImpl(DispatchRepository dispatchRepository,
                               ObjectProvider<OmsClient> omsClientProvider,
                               HttpServletRequest httpServletRequest,
                               ObjectProvider<FacilityRouteClient> facilityRouteClientProvider,
                               ManifestOutbox manifestOutbox, BagPackingService bagPacking) {
        this.dispatchRepository = dispatchRepository;
        this.omsClientProvider = omsClientProvider;
        this.httpServletRequest = httpServletRequest;
        this.manifestOutbox = manifestOutbox;
        this.bagPacking = bagPacking;
        this.facilityRouteClientProvider = facilityRouteClientProvider;
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
        assertBagOriginOwned(requireCurrentUser(), request.getOriginFacilityCode());
        List<String> mailNos = normalizePackingMailNos(request.getMailNoList());
        if (mailNos == null || mailNos.isEmpty()) {
            throw new BusinessException(400, "mailNoList cannot be empty");
        }
        List<MailDispatchCandidateResponse> candidates = loadCandidates(request.getOriginFacilityCode(), request.getDestinationFacilityCode(), request.getMailTypeCode(), request.getMailScope(), request.getDestCountryCode());
        validateCandidates(candidates, mailNos);
        candidates = candidates.stream().filter(candidate -> mailNos.contains(candidate.getWaybillNo())).toList();

        String effectiveCountryCode = resolveEffectiveDestCountryCode(request.getDestCountryCode(), candidates);
        String destinationFacilityCode = resolveDestinationFacilityCode(request.getDestinationFacilityCode(), candidates);
        boolean international = effectiveCountryCode != null && !isDomesticCountry(effectiveCountryCode);
        String nextHop = international || request.getRouteCode() != null && !request.getRouteCode().isBlank()
                ? destinationFacilityCode : resolveDomesticNextHop(request.getOriginFacilityCode(), destinationFacilityCode);
        RouteRuleEntity route = resolveRoute(request.getRouteCode(), request.getOriginFacilityCode(), nextHop,
                international ? effectiveCountryCode : null, international);
        // Post offices can form a new total package before sorting/export route is known.
        // If a route exists, keep it on the bag; otherwise downstream sorting resolves it later.
        String resolvedTargetFacilityCode = resolveRouteTarget(route, nextHop);
        if (international && route != null && route.getExportFacilityCode() != null && !route.getExportFacilityCode().isBlank()) {
            resolvedTargetFacilityCode = route.getExportFacilityCode();
        }

        DispatchBagEntity entity = new DispatchBagEntity();
        entity.setOriginFacilityCode(request.getOriginFacilityCode());
        entity.setDestinationFacilityCode(resolvedTargetFacilityCode);
        entity.setRouteCode(route == null ? request.getRouteCode() : route.getRuleCode());
        entity.setFacilityRouteCode(resolveFacilityRouteCode(request.getOriginFacilityCode(), resolvedTargetFacilityCode,
                route == null ? null : route.getTransportMode()));
        entity.setMailTypeCode(request.getMailTypeCode());
        entity.setMailNoListAsList(mailNos);
        entity.setMailCount(mailNos.size());
        List<MailDispatchCandidateResponse> selectedCandidates = candidates;
        List<PackingConfirmation.Item> packingItems = mailNos.stream().map(no -> {
            var candidate = selectedCandidates.stream().filter(item -> no.equals(item.getWaybillNo())).findFirst().orElseThrow();
            return new PackingConfirmation.Item(no, candidate.getWeightGrams());
        }).toList();
        entity.setTotalWeightGrams(totalPackingWeight(packingItems));
        entity.setStatus(DispatchConstants.BAG_STATUS_CREATED);
        entity = dispatchRepository.saveDispatchBag(entity);

        bagPacking.prepare(new PackingConfirmation(java.util.UUID.randomUUID().toString(), entity.getBagNo(),
                entity.getOriginFacilityCode(), 1, packingItems));
        return toBagResponse(entity);
    }

    @Override
    @Transactional
    public DispatchBatchResponse createDispatchBatch(DispatchBatchCreateRequest request) {
        JwtUserContext user = requireCurrentUser();
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
            if (candidate.getLastReceiptEventNo() != null) throw new BusinessException(409, "received bags must use the custody forwarding workflow");
            assertBagOriginOwned(user, candidate.getOriginFacilityCode());
            bagPacking.requireConfirmed(candidate.getBagNo());
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
        entity.setSourceOrgCode(bag.getOriginFacilityCode());
        entity.setDestinationOrgCode(bag.getDestinationFacilityCode());
        entity.setFacilityRouteCode(bag.getFacilityRouteCode());
        entity.setStatus(DispatchConstants.BATCH_STATUS_PENDING);
        entity = dispatchRepository.saveDispatchBatch(entity);
        List<String> bagNos = bags.stream().map(DispatchBagEntity::getBagNo).toList();
        dispatchRepository.replaceBatchBags(entity.getBatchNo(), bagNos);
        entity.setBagNos(bagNos);
        syncManifestToSorting(entity, routeCode, bag.getOriginFacilityCode(),
                bag.getDestinationFacilityCode(), bags);
        return toBatchResponse(entity);
    }

    @Override
    @Transactional
    public DispatchBatchResponse approveDispatchBatch(String batchNo, DispatchBatchApproveRequest request) {
        JwtUserContext user = requireCurrentUser();
        DispatchBatchEntity batch = dispatchRepository.lockDispatchBatchByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(404, "dispatch batch not found"));
        for (String bagNo : getBatchBagNos(batch)) {
            DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(bagNo)
                    .orElseThrow(() -> new BusinessException(404, "dispatch bag not found: " + bagNo));
            assertBagOriginOwned(user, batch.getSourceOrgCode() == null ? bag.getOriginFacilityCode() : batch.getSourceOrgCode());
        }
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
    public DispatchBatchResponse sealDispatchBatch(String batchNo) {
        JwtUserContext user = requireCurrentUser();
        DispatchBatchEntity batch = dispatchRepository.lockDispatchBatchByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(404, "dispatch batch not found"));
        if (!java.util.Set.of(DispatchConstants.BATCH_STATUS_PENDING, DispatchConstants.BATCH_STATUS_APPROVED).contains(batch.getStatus())) {
            throw new BusinessException(409, "only batches awaiting handoff may request sealing");
        }
        List<String> bagNos = getBatchBagNos(batch);
        if (bagNos.isEmpty()) throw new BusinessException(409, "batch has no bags");
        for (String bagNo : bagNos) {
            DispatchBagEntity bag = dispatchRepository.findDispatchBagByBagNo(bagNo)
                    .orElseThrow(() -> new BusinessException(409, "batch bag is missing"));
            assertBagOriginOwned(user, batch.getSourceOrgCode() == null ? bag.getOriginFacilityCode() : batch.getSourceOrgCode());
            bagPacking.requireConfirmed(bagNo);
        }
        String actor = user.getUserId() == null ? user.getUsername() : user.getUserId().toString();
        manifestOutbox.enqueueSeal(batch.getManifestNo(), actor);
        return toBatchResponse(batch);
    }

    @Override
    @Transactional
    public DispatchBatchResponse cancelDispatchBatch(String batchNo, com.liana.post.dispatch.model.dto.DispatchBatchCancelRequest request) {
        JwtUserContext user = requireCurrentUser();
        if (request == null || request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 512) {
            throw new BusinessException(400, "请填写 512 字以内的作废原因");
        }
        DispatchBatchEntity batch = dispatchRepository.lockDispatchBatchByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException(404, "dispatch batch not found"));
        if (!java.util.Set.of("PENDING", "APPROVED", "CANCELLING", "CANCELLED").contains(batch.getStatus())) {
            throw new BusinessException(409, "已交接批次不能作废，请通过后续交接或差异处理保留事实");
        }
        if ("CANCELLED".equals(batch.getStatus())) {
            assertBagOriginOwned(user, batch.getSourceOrgCode() == null
                    ? dispatchRepository.findDispatchBagByBagNo(batch.getBagNo()).orElseThrow().getOriginFacilityCode() : batch.getSourceOrgCode());
            return toBatchResponse(batch);
        }
        List<String> bagNos = getBatchBagNos(batch).stream().sorted().toList();
        if (bagNos.isEmpty()) throw new BusinessException(409, "batch has no bags");
        for (String bagNo : bagNos) {
            var bag = dispatchRepository.lockDispatchBagByBagNo(bagNo)
                    .orElseThrow(() -> new BusinessException(409, "batch bag is missing"));
            assertBagOriginOwned(user, batch.getSourceOrgCode() == null ? bag.getOriginFacilityCode() : batch.getSourceOrgCode());
            if (!java.util.Set.of("CREATED", "REVIEWED").contains(bag.getStatus())
                    || dispatchRepository.findHandoffRecordByBagNo(bagNo).isPresent()) {
                throw new BusinessException(409, "已有总包交接或到达事实，不能作废路单");
            }
            if (bag.getTransportTaskCode() != null && !bag.getTransportTaskCode().isBlank()) {
                throw new BusinessException(409, "已有总包关联运输任务；任务取消与路单作废须通过后续分配流程分别处理");
            }
        }
        String actor = user.getUserId() == null ? user.getUsername() : user.getUserId().toString();
        manifestOutbox.enqueueCancel(batch.getManifestNo(), actor, request.reason());
        if (!java.util.Set.of("CANCELLING", "CANCELLED").contains(batch.getStatus())) {
            batch.setStatus(DispatchConstants.BATCH_STATUS_CANCELLING);
            batch.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            dispatchRepository.updateDispatchBatch(batch);
        }
        return toBatchResponse(batch);
    }

    @Override
    @Transactional
    public HandoffRecordResponse createHandoffRecord(HandoffCreateRequest request) {
        String currentFacilityCode = resolveCurrentFacilityCode();
        if (currentFacilityCode == null || currentFacilityCode.isBlank()) {
            throw new BusinessException(401, "current facility cannot be resolved from token");
        }

        // All cross-bag operations lock the batch first, then bags. This serializes handoff vs cancellation.
        DispatchBatchEntity batch = dispatchRepository.lockDispatchBatchByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(409, "请先生成配发批次与路单，等待接收端确认预告后交接"));
        requireOperableBatch(batch);
        DispatchBagEntity bag = dispatchRepository.lockDispatchBagByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found"));
        String sourceFacilityCode = batch.getSourceOrgCode() == null ? bag.getOriginFacilityCode() : batch.getSourceOrgCode();
        if (!currentFacilityCode.equalsIgnoreCase(sourceFacilityCode)) {
            throw new BusinessException(403, "current facility does not own this dispatch bag");
        }
        String destinationFacilityCode = batch.getDestinationOrgCode() == null ? bag.getDestinationFacilityCode() : batch.getDestinationOrgCode();
        if (destinationFacilityCode == null || destinationFacilityCode.isBlank()) {
            destinationFacilityCode = resolveFacilityRouteDestination(currentFacilityCode, request.getToFacilityCode());
            bag.setDestinationFacilityCode(destinationFacilityCode);
            bag.setUpdatedAt(LocalDateTime.now());
            dispatchRepository.updateDispatchBag(bag);
        }
        if (!destinationFacilityCode.equalsIgnoreCase(request.getToFacilityCode().trim())) {
            throw new BusinessException(400, "handoff destination must match the bag destination");
        }
        if (DispatchConstants.BAG_STATUS_DISPATCHED.equals(bag.getStatus())
                || dispatchRepository.findHandoffRecordByBagNo(bag.getBagNo()).isPresent()) {
            throw new BusinessException(409, "dispatch bag has already been handed off");
        }
        if (!"SENT".equals(manifestOutbox.status(batch.getManifestNo()))) {
            throw new BusinessException(409, "路单预告尚未获接收端确认，请在批次页面查看预告状态并稍后重试");
        }
        manifestOutbox.requireSealed(batch.getManifestNo());
        manifestOutbox.requireAssigned(batch.getManifestNo(), bag.getTransportTaskCode());

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
        if (batchBags.size() != batchBagNos.size() || (batch.getSourceOrgCode() == null && batchBags.stream().anyMatch(item ->
                !currentFacilityCode.equalsIgnoreCase(item.getOriginFacilityCode())
                        || (item.getDestinationFacilityCode() != null && !item.getDestinationFacilityCode().isBlank()
                        && !request.getToFacilityCode().equalsIgnoreCase(item.getDestinationFacilityCode()))))) {
            throw new BusinessException(409, "all bags in a batch must be handed off together along their planned route");
        }
        String resolvedDestinationFacilityCode = destinationFacilityCode;
        batchBags.stream().filter(item -> item.getDestinationFacilityCode() == null || item.getDestinationFacilityCode().isBlank())
                .forEach(item -> {
                    item.setDestinationFacilityCode(resolvedDestinationFacilityCode);
                    item.setUpdatedAt(LocalDateTime.now());
                    dispatchRepository.updateDispatchBag(item);
                });
        syncManifestToSorting(batch, batch.getRouteCode(), sourceFacilityCode,
                request.getToFacilityCode(), batchBags);

        return toHandoffResponse(entity);
    }

    @Override
    @Transactional
    public Boolean markManifestArrived(ManifestArrivedRequest request) {
        throw new BusinessException(409, "use authenticated immutable bag receipt events");
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
        DispatchBagEntity bag = dispatchRepository.lockDispatchBagByBagNo(request.getBagNo())
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found"));
        assertBagOriginOwned(requireCurrentUser(), bag.getOriginFacilityCode());
        if (!bag.getOriginFacilityCode().equalsIgnoreCase(request.getCurrentFacilityCode())) {
            throw new BusinessException(403, "packing facility cannot change");
        }
        List<String> expected = bagPacking.items(bag.getBagNo()).stream().map(PackingConfirmation.Item::mailNo).toList();
        if (!expected.equals(normalizePackingMailNos(request.getMailNoList()))) {
            throw new BusinessException(409, "总包装包清单已固定，不能通过同步接口覆盖");
        }
        return toBagResponse(bag);
    }

    private void syncManifestToSorting(DispatchBatchEntity batch, String routeCode,
                                       String sourceFacilityCode, String destinationFacilityCode, List<DispatchBagEntity> bags) {
        ManifestDTO request = new ManifestDTO();
        request.setManifestCode(batch.getManifestNo());
        request.setBatchNo(batch.getBatchNo());
        request.setVersionNo(1);
        request.setOriginNode(sourceFacilityCode);
        request.setTargetNode(destinationFacilityCode);
        long totalWeight = 0;
        boolean allWeightsKnown = true;
        List<TotalPackageDTO> packages = new java.util.ArrayList<>();
        for (DispatchBagEntity bag : bags) {
            bagPacking.requireConfirmed(bag.getBagNo());
            List<PackingConfirmation.Item> packingItems = bagPacking.items(bag.getBagNo());
            if (packingItems.isEmpty()) throw new BusinessException(409, "structured packing is missing");
            Integer packageWeight = totalPackingWeight(packingItems);
            if (packageWeight == null) allWeightsKnown = false;
            else totalWeight += packageWeight;
            TotalPackageDTO totalPackage = new TotalPackageDTO();
            totalPackage.setPackageNo(bag.getBagNo());
            totalPackage.setPackageLevel("TRANSIT");
            totalPackage.setRouteCode(routeCode);
            totalPackage.setPackageWeight(packageWeight == null ? null : java.math.BigDecimal.valueOf(packageWeight));
            List<TotalPackageDTO.ManifestItemDTO> items = new java.util.ArrayList<>();
            for (PackingConfirmation.Item packedItem : packingItems) {
                TotalPackageDTO.ManifestItemDTO item = new TotalPackageDTO.ManifestItemDTO();
                item.setItemNo(packedItem.mailNo());
                item.setWeight(packedItem.weightGrams() == null ? null : java.math.BigDecimal.valueOf(packedItem.weightGrams()));
                item.setSeqNo(items.size() + 1);
                item.setRouteCode(routeCode);
                items.add(item);
            }
            totalPackage.setItems(items);
            packages.add(totalPackage);
        }
        request.setTotalPackages(packages);
        request.setTotalWeight(allWeightsKnown ? java.math.BigDecimal.valueOf(totalWeight) : null);
        manifestOutbox.enqueue(request);
    }

    @Override
    public DispatchBagResponse getDispatchBagById(Long id) {
        return toBagResponse(dispatchRepository.findDispatchBagById(id)
                .orElseThrow(() -> new BusinessException(404, "dispatch bag not found: " + id)));
    }

    @Override
    public DispatchBagResponse linkTransportTask(Long id, DispatchTransportTaskLinkRequest request) {
        throw new BusinessException(409, "逐包关联已关闭，请使用整张已封路单的运输分配协议");
    }

    @Override
    public List<DispatchBagResponse> listDispatchBags() {
        JwtUserContext user = requireCurrentUser();
        return dispatchRepository.findAllDispatchBags().stream()
                .filter(bag -> isAdmin(user) || belongsToFacility(bag, user.getFacilityCode()) || dispatchRepository.findDispatchBatchByBagNo(bag.getBagNo())
                        .map(b -> matchesFacility(b.getSourceOrgCode(), user.getFacilityCode()) || matchesFacility(b.getDestinationOrgCode(), user.getFacilityCode())).orElse(false))
                .map(this::toBagResponse).toList();
    }

    @Override
    public List<DispatchBatchResponse> listDispatchBatches() {
        JwtUserContext user = requireCurrentUser();
        if (isAdmin(user)) {
            return dispatchRepository.findAllDispatchBatches().stream().map(this::toBatchResponse).toList();
        }
        Set<String> ownedBagNos = dispatchRepository.findAllDispatchBags().stream()
                .filter(bag -> belongsToFacility(bag, user.getFacilityCode()))
                .map(DispatchBagEntity::getBagNo).collect(Collectors.toSet());
        return dispatchRepository.findAllDispatchBatches().stream()
                .filter(batch -> matchesFacility(batch.getSourceOrgCode(), user.getFacilityCode())
                        || matchesFacility(batch.getDestinationOrgCode(), user.getFacilityCode())
                        || (batch.getSourceOrgCode() == null && getBatchBagNos(batch).stream().anyMatch(ownedBagNos::contains)))
                .map(this::toBatchResponse).toList();
    }

    @Override
    public List<HandoffRecordResponse> listHandoffRecords() {
        JwtUserContext user = requireCurrentUser();
        return dispatchRepository.findAllHandoffRecords().stream()
                .filter(record -> isAdmin(user)
                        || matchesFacility(record.getFromFacilityCode(), user.getFacilityCode())
                        || matchesFacility(record.getToFacilityCode(), user.getFacilityCode()))
                .map(this::toHandoffResponse).toList();
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

    private List<String> normalizePackingMailNos(List<String> mailNos) {
        if (mailNos == null || mailNos.isEmpty()) throw new BusinessException(400, "mailNoList cannot be empty");
        List<String> normalized = mailNos.stream().map(no -> normalizeRequired(no, "mailNo")).toList();
        if (new HashSet<>(normalized).size() != normalized.size()) throw new BusinessException(400, "duplicate mail in packing");
        return normalized;
    }

    private Integer totalPackingWeight(List<PackingConfirmation.Item> items) {
        if (items.stream().anyMatch(item -> item.weightGrams() == null)) return null;
        long total = items.stream().mapToLong(item -> item.weightGrams().longValue()).sum();
        if (total > Integer.MAX_VALUE) throw new BusinessException(400, "packing weight exceeds supported capacity");
        return (int) total;
    }

    private DispatchBagResponse toBagResponse(DispatchBagEntity entity) {
        DispatchBagResponse response = new DispatchBagResponse();
        response.setId(entity.getId());
        response.setDispatchBagId(entity.getId());
        response.setBagNo(entity.getBagNo());
        response.setOriginFacilityCode(entity.getOriginFacilityCode());
        response.setDestinationFacilityCode(entity.getDestinationFacilityCode());
        response.setRouteCode(entity.getRouteCode());
        response.setFacilityRouteCode(entity.getFacilityRouteCode());
        response.setTransportTaskCode(entity.getTransportTaskCode());
        var manifestBatch = dispatchRepository.findDispatchBatchByBagNo(entity.getBagNo()).orElse(null);
        if (manifestBatch != null) {
            response.setManifestNo(manifestBatch.getManifestNo());
            response.setManifestBatchStatus(manifestBatch.getStatus());
            response.setCurrentSourceOrgCode(manifestBatch.getSourceOrgCode());
            response.setCurrentDestinationOrgCode(manifestBatch.getDestinationOrgCode());
        }
        response.setStatus(effectiveBagStatus(entity));
        response.setMailTypeCode(entity.getMailTypeCode());
        List<PackingConfirmation.Item> items = bagPacking.items(entity.getBagNo());
        response.setMailNoList(items.stream().map(PackingConfirmation.Item::mailNo).toList());
        response.setMailCount(items.size());
        Integer totalWeight = items.isEmpty() ? null : totalPackingWeight(items);
        response.setTotalWeightGrams(totalWeight);
        BagPackingService.State packingState = bagPacking.state(entity.getBagNo());
        response.setPackingVersion(packingState.packingVersion());
        response.setPackingSyncStatus(packingState.syncStatus());
        response.setPackingError(packingState.lastError());
        response.setWeightSource(totalWeight == null ? "UNKNOWN" : "OMS_MAIL_SUM");
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
        response.setManifestNo(entity.getManifestNo());
        response.setSourceOrgCode(entity.getSourceOrgCode());
        response.setDestinationOrgCode(entity.getDestinationOrgCode());
        response.setFacilityRouteCode(entity.getFacilityRouteCode());
        response.setManifestSyncStatus(manifestOutbox.status(entity.getManifestNo()));
        var seal = manifestOutbox.sealState(entity.getManifestNo());
        response.setManifestSealStatus(seal == null ? "NOT_REQUESTED" : seal.status());
        response.setManifestSealError(seal == null ? null : seal.lastError());
        var cancel = manifestOutbox.cancelState(entity.getManifestNo());
        response.setManifestCancelStatus(cancel == null ? "NOT_REQUESTED" : cancel.status());
        response.setManifestCancelError(cancel == null ? null : cancel.lastError());
        response.setManifestCancelReason(cancel == null ? null : cancel.reason());
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
        response.setBatchNo(entity.getBatchNo());
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

    private String resolveFacilityRouteDestination(String originFacilityCode, String requestedDestination) {
        FacilityRouteClient client = facilityRouteClientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException(503, "facility route service is unavailable");
        }
        try {
            var result = client.listRoutes();
            boolean routeExists = result != null && result.isSuccess() && result.getData() != null
                    && result.getData().stream().anyMatch(route -> Integer.valueOf(1).equals(route.getStatus())
                    && route.getOriginFacilityCode() != null && route.getOriginFacilityCode().equalsIgnoreCase(originFacilityCode)
                    && route.getDestinationFacilityCode() != null && route.getDestinationFacilityCode().equalsIgnoreCase(requestedDestination.trim()));
            if (!routeExists) {
                throw new BusinessException(400, "handoff destination must match an active facility route");
            }
            return requestedDestination.trim().toUpperCase();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(503, "failed to verify handoff facility route");
        }
    }

    private String resolveFacilityRouteCode(String origin, String destination, String mode) {
        FacilityRouteClient client = facilityRouteClientProvider.getIfAvailable();
        if (client == null) throw new BusinessException(503, "facility route service is unavailable");
        try {
            var result = client.listRoutes();
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BusinessException(503, "facility route lookup failed");
            }
            return result.getData().stream()
                    .filter(edge -> Integer.valueOf(1).equals(edge.getStatus()))
                    .filter(edge -> origin.equalsIgnoreCase(edge.getOriginFacilityCode())
                            && destination.equalsIgnoreCase(edge.getDestinationFacilityCode()))
                    .filter(edge -> mode == null || mode.equalsIgnoreCase(edge.getTransportMode())
                            || ("TRUCK".equalsIgnoreCase(mode) && "LAND".equalsIgnoreCase(edge.getTransportMode())))
                    .map(FacilityRouteClient.FacilityRouteView::getRouteCode)
                    .sorted().findFirst().orElse(null);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(503, "facility route lookup failed");
        }
    }

    private String resolveDomesticNextHop(String origin, String destination) {
        if (origin == null || destination == null || origin.equalsIgnoreCase(destination)) return destination;
        FacilityRouteClient client = facilityRouteClientProvider.getIfAvailable();
        if (client == null) throw new BusinessException(503, "facility route service is unavailable");
        var result = client.listRoutes();
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(503, "facility route lookup failed");
        }
        Map<String, List<FacilityRouteClient.FacilityRouteView>> graph = new HashMap<>();
        for (var edge : result.getData()) {
            if (edge != null && Integer.valueOf(1).equals(edge.getStatus())
                    && edge.getOriginFacilityCode() != null && edge.getDestinationFacilityCode() != null) {
                graph.computeIfAbsent(edge.getOriginFacilityCode().toUpperCase(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        graph.values().forEach(edges -> edges.sort(Comparator
                .comparing((FacilityRouteClient.FacilityRouteView edge) -> edge.getPriorityLevel() == null ? 0 : edge.getPriorityLevel())
                .thenComparing(edge -> edge.getEstimatedHours() == null ? java.math.BigDecimal.ZERO : edge.getEstimatedHours())
                .thenComparing(FacilityRouteClient.FacilityRouteView::getRouteCode)));
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> firstHop = new HashMap<>();
        String source = origin.toUpperCase();
        String target = destination.toUpperCase();
        queue.add(source);
        firstHop.put(source, source);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (var edge : graph.getOrDefault(current, List.of())) {
                String next = edge.getDestinationFacilityCode().toUpperCase();
                if (firstHop.containsKey(next)) continue;
                firstHop.put(next, current.equals(source) ? next : firstHop.get(current));
                if (next.equals(target)) return firstHop.get(next);
                queue.add(next);
            }
        }
        return destination;
    }

    private JwtUserContext requireCurrentUser() {
        String authorization = httpServletRequest.getHeader("Authorization");
        if (authorization == null || !authorization.matches("(?i)^Bearer\\s+\\S+$")) {
            throw new BusinessException(401, "valid bearer token is required");
        }
        JwtUserContext user = JwtTokenUtil.getUserContextFromToken(authorization.replaceFirst("(?i)^Bearer\\s+", ""));
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            throw new BusinessException(401, "valid bearer token is required");
        }
        if (!isAdmin(user) && (user.getFacilityCode() == null || user.getFacilityCode().isBlank())) {
            throw new BusinessException(403, "current facility cannot be resolved from token");
        }
        return user;
    }

    private boolean isAdmin(JwtUserContext user) {
        return AuthConstants.ROLE_ADMIN.equalsIgnoreCase(user.getRole());
    }

    private void assertBagOriginOwned(JwtUserContext user, String originFacilityCode) {
        if (!isAdmin(user) && !matchesFacility(originFacilityCode, user.getFacilityCode())) {
            throw new BusinessException(403, "current facility does not own this dispatch bag");
        }
    }

    private void requireOperableBatch(DispatchBatchEntity batch) {
        if (java.util.Set.of("CANCELLING", "CANCELLED").contains(batch.getStatus())) {
            throw new BusinessException(409, "路单正在作废或已作废，禁止交接和新增运输关联");
        }
    }

    private boolean belongsToFacility(DispatchBagEntity bag, String facilityCode) {
        return matchesFacility(bag.getOriginFacilityCode(), facilityCode)
                || matchesFacility(bag.getDestinationFacilityCode(), facilityCode);
    }
}
