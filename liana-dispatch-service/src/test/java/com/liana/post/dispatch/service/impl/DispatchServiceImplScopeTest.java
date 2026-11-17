package com.liana.post.dispatch.service.impl;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.dispatch.client.OmsClient;
import com.liana.post.dispatch.client.FacilityRouteClient;
import com.liana.post.dispatch.model.dto.DispatchBatchCreateRequest;
import com.liana.post.dispatch.model.dto.HandoffCreateRequest;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
import com.liana.post.dispatch.service.ManifestOutbox;
import com.liana.post.dispatch.service.BagPackingService;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

class DispatchServiceImplScopeTest {

    private DispatchRepository repository;
    private HttpServletRequest httpRequest;
    private DispatchServiceImpl service;
    private ManifestOutbox outbox;
    private BagPackingService packing;
    private ObjectProvider<OmsClient> omsClientProvider;
    private ObjectProvider<FacilityRouteClient> facilityRouteClientProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(DispatchRepository.class);
        when(repository.lockDispatchBatchByBatchNo(any())).thenAnswer(i -> repository.findDispatchBatchByBatchNo(i.getArgument(0)));
        when(repository.lockDispatchBatchByBagNo(any())).thenAnswer(i -> repository.findDispatchBatchByBagNo(i.getArgument(0)));
        httpRequest = mock(HttpServletRequest.class);
        omsClientProvider = mock(ObjectProvider.class);
        facilityRouteClientProvider = mock(ObjectProvider.class);
        outbox = mock(ManifestOutbox.class);
        packing = mock(BagPackingService.class);
        when(packing.state(any())).thenReturn(new BagPackingService.State(1, "CONFIRMED", null));
        service = new DispatchServiceImpl(repository, omsClientProvider, httpRequest, facilityRouteClientProvider, outbox, packing);
        String token = JwtTokenUtil.generateToken("oro-clerk", "CLERK", "LN-OR-01");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
    }

    @Test
    void clerkListsOnlyBagsAndBatchesConnectedToTheirFacility() {
        DispatchBagEntity outbound = bag("ORO-OUT", "LN-OR-01", "LN-NAM-01");
        DispatchBagEntity inbound = bag("ORO-IN", "LN-NAM-01", "LN-OR-01");
        DispatchBagEntity other = bag("NAM-OUT", "B1", "C1");
        when(repository.findAllDispatchBags()).thenReturn(List.of(outbound, inbound, other));

        DispatchBatchEntity inboundBatch = batch("B-ORO", "ORO-IN", List.of("ORO-IN"));
        DispatchBatchEntity otherBatch = batch("B-NAM", "NAM-OUT", List.of("NAM-OUT"));
        when(repository.findAllDispatchBatches()).thenReturn(List.of(inboundBatch, otherBatch));

        assertEquals(List.of("ORO-OUT", "ORO-IN"), service.listDispatchBags().stream().map(item -> item.getBagNo()).toList());
        assertEquals(List.of("B-ORO"), service.listDispatchBatches().stream().map(item -> item.getBatchNo()).toList());
    }

    @Test
    void clerkCannotCreateBatchFromAnotherFacility() {
        when(repository.findDispatchBagByBagNo("NAM-OUT"))
                .thenReturn(Optional.of(bag("NAM-OUT", "B1", "C1")));
        DispatchBatchCreateRequest request = new DispatchBatchCreateRequest();
        request.setBagNo("NAM-OUT");

        assertThrows(BusinessException.class, () -> service.createDispatchBatch(request));
    }

    @Test
    void handoffRequiresPreviouslyAcknowledgedPrealertBeforeAnyHandoffFact() {
        var bag = bag("ORO-OUT", "LN-OR-01", "LN-OR-RC");
        var batch = batch("D-TEST", "ORO-OUT", List.of("ORO-OUT"));
        batch.setManifestNo("M-INDEPENDENT");
        when(repository.lockDispatchBagByBagNo("ORO-OUT")).thenReturn(Optional.of(bag));
        when(repository.findDispatchBatchByBagNo("ORO-OUT")).thenReturn(Optional.of(batch));
        when(outbox.status("M-INDEPENDENT")).thenReturn("RETRY");
        var r = new HandoffCreateRequest(); r.setBagNo("ORO-OUT"); r.setToFacilityCode("LN-OR-RC");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.createHandoffRecord(r)).getCode());
        verify(repository, never()).saveHandoffRecord(any());
        verify(repository, never()).updateDispatchBag(any());
    }

    @Test
    void batchPrealertPreservesUnknownWeightInsteadOfPublishingZeroOrPartialTotal() {
        var known = bag("P1", "LN-OR-01", "LN-OR-RC");
        known.setTotalWeightGrams(100);
        var unknown = bag("P2", "LN-OR-01", "LN-OR-RC");
        when(repository.findDispatchBagByBagNo("P1")).thenReturn(Optional.of(known));
        when(repository.findDispatchBagByBagNo("P2")).thenReturn(Optional.of(unknown));
        when(packing.items("P1")).thenReturn(List.of(new PackingConfirmation.Item("CP000000001LN", 100)));
        when(packing.items("P2")).thenReturn(List.of(new PackingConfirmation.Item("CP000000002LN", null)));
        when(repository.saveDispatchBatch(any())).thenAnswer(invocation -> {
            DispatchBatchEntity saved = invocation.getArgument(0);
            saved.setBatchNo("D1"); saved.setManifestNo("M1");
            return saved;
        });
        var request = new DispatchBatchCreateRequest();
        request.setBagNos(List.of("P1", "P2"));

        service.createDispatchBatch(request);

        var captured = org.mockito.ArgumentCaptor.forClass(com.liana.post.common.dto.sorting.ManifestDTO.class);
        verify(outbox).enqueue(captured.capture());
        org.junit.jupiter.api.Assertions.assertNull(captured.getValue().getTotalWeight());
        assertEquals(new java.math.BigDecimal("100"), captured.getValue().getTotalPackages().get(0).getPackageWeight());
        org.junit.jupiter.api.Assertions.assertNull(captured.getValue().getTotalPackages().get(1).getPackageWeight());
    }

    @Test
    void handoffRequiresSealConfirmationEvenWhenPrealertWasAcknowledged() {
        var bag = bag("ORO-OUT", "LN-OR-01", "LN-OR-RC");
        var batch = batch("D-TEST", "ORO-OUT", List.of("ORO-OUT")); batch.setManifestNo("M1");
        when(repository.lockDispatchBagByBagNo("ORO-OUT")).thenReturn(Optional.of(bag));
        when(repository.findDispatchBatchByBagNo("ORO-OUT")).thenReturn(Optional.of(batch));
        when(outbox.status("M1")).thenReturn("SENT");
        org.mockito.Mockito.doThrow(new BusinessException(409, "seal pending")).when(outbox).requireSealed("M1");
        var request = new HandoffCreateRequest(); request.setBagNo("ORO-OUT"); request.setToFacilityCode("LN-OR-RC");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.createHandoffRecord(request)).getCode());
        verify(repository, never()).saveHandoffRecord(any());
        verify(repository, never()).updateDispatchBatch(any());
    }

    @Test
    void sealUsesAuthenticatedActorAndRejectsOtherFacility() {
        var batch = batch("D-TEST", "P1", List.of("P1")); batch.setManifestNo("M1"); batch.setStatus("PENDING");
        when(repository.findDispatchBatchByBatchNo("D-TEST")).thenReturn(Optional.of(batch));
        when(repository.findDispatchBagByBagNo("P1")).thenReturn(Optional.of(bag("P1", "LN-NAM-HQ", "LN-OR-01")));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.sealDispatchBatch("D-TEST")).getCode());
        verify(outbox, never()).enqueueSeal(any(), any());
        when(repository.findDispatchBagByBagNo("P1")).thenReturn(Optional.of(bag("P1", "LN-OR-01", "LN-OR-RC")));
        service.sealDispatchBatch("D-TEST");
        verify(outbox).enqueueSeal("M1", "oro-clerk");
    }

    @Test
    void pendingPackingCannotCreateBatchOrManifest() {
        var pending = bag("P1", "LN-OR-01", "LN-OR-RC");
        when(repository.findDispatchBagByBagNo("P1")).thenReturn(Optional.of(pending));
        org.mockito.Mockito.doThrow(new BusinessException(409, "packing is pending")).when(packing).requireConfirmed("P1");
        var request = new DispatchBatchCreateRequest(); request.setBagNo("P1");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.createDispatchBatch(request)).getCode());
        verify(repository, never()).saveDispatchBatch(any());
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void syncEndpointCannotOverwritePackingOrRegressBagStatus() {
        var dispatched = bag("P1", "LN-OR-01", "LN-OR-RC");
        dispatched.setStatus("DISPATCHED");
        when(repository.lockDispatchBagByBagNo("P1")).thenReturn(Optional.of(dispatched));
        when(packing.items("P1")).thenReturn(List.of(new PackingConfirmation.Item("CP000000001LN", 125)));
        var request = new com.liana.post.dispatch.model.dto.DispatchMailSyncRequest();
        request.setBagNo("P1"); request.setCurrentFacilityCode("LN-OR-01");
        request.setMailNoList(List.of("CP000000002LN"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.syncMailBag(request)).getCode());
        request.setMailNoList(List.of("CP000000001LN"));
        assertEquals("DISPATCHED", service.syncMailBag(request).getStatus());
        verify(repository, never()).updateDispatchBag(any());
    }

    @Test
    void creatingBagUsesSelectedOmsWeightsAndPersistsConfirmationWithoutRemoteMutation() {
        var oms = mock(OmsClient.class); when(omsClientProvider.getIfAvailable()).thenReturn(oms);
        var routes = mock(FacilityRouteClient.class); when(facilityRouteClientProvider.getIfAvailable()).thenReturn(routes);
        when(routes.listRoutes()).thenReturn(com.liana.post.common.model.Result.ok(List.of()));
        var selected = new com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse();
        selected.setWaybillNo("CP000000001LN"); selected.setWeightGrams(321); selected.setDestCountryCode("LN");
        var other = new com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse();
        other.setWaybillNo("CP000000002LN"); other.setWeightGrams(999); other.setDestCountryCode("AU");
        when(oms.listDispatchCandidates(any())).thenReturn(com.liana.post.common.model.Result.ok(List.of(selected, other)));
        when(repository.saveDispatchBag(any())).thenAnswer(invocation -> {
            DispatchBagEntity saved = invocation.getArgument(0); saved.setBagNo("P1"); return saved;
        });
        when(packing.items("P1")).thenReturn(List.of(new PackingConfirmation.Item("CP000000001LN", 321)));
        var request = new com.liana.post.dispatch.model.dto.DispatchBagCreateRequest();
        request.setOriginFacilityCode("LN-OR-01"); request.setDestinationFacilityCode("LN-OR-RC");
        request.setMailTypeCode("C"); request.setRouteCode("RULE-1");
        request.setMailNoList(List.of("CP000000001LN"));

        var response = service.createDispatchBag(request);

        assertEquals(321, response.getTotalWeightGrams());
        assertEquals("OMS_MAIL_SUM", response.getWeightSource());
        var captured = org.mockito.ArgumentCaptor.forClass(PackingConfirmation.class);
        verify(packing).prepare(captured.capture());
        assertEquals(List.of(new PackingConfirmation.Item("CP000000001LN", 321)), captured.getValue().items());
        verify(repository, never()).updateDispatchBag(any());
        verify(oms).listDispatchCandidates(any());
        org.mockito.Mockito.verifyNoMoreInteractions(oms);
    }

    private DispatchBagEntity bag(String bagNo, String origin, String destination) {
        DispatchBagEntity bag = new DispatchBagEntity();
        bag.setBagNo(bagNo);
        bag.setOriginFacilityCode(origin);
        bag.setDestinationFacilityCode(destination);
        bag.setStatus("CREATED");
        return bag;
    }

    @Test
    void cancellationUsesLoginActorAndStopsFurtherHandoffAndTaskLinkImmediately() {
        var bag = bag("P1", "LN-OR-01", "LN-OR-RC"); bag.setId(7L);
        var batch = batch("D1", "P1", List.of("P1")); batch.setManifestNo("M1");
        when(repository.findDispatchBatchByBatchNo("D1")).thenReturn(Optional.of(batch));
        when(repository.findDispatchBatchByBagNo("P1")).thenReturn(Optional.of(batch));
        when(repository.lockDispatchBagByBagNo("P1")).thenReturn(Optional.of(bag));
        when(repository.findDispatchBagById(7L)).thenReturn(Optional.of(bag));
        var response = service.cancelDispatchBatch("D1", new com.liana.post.dispatch.model.dto.DispatchBatchCancelRequest("撤销配发"));
        assertEquals("CANCELLING", response.getStatus());
        verify(outbox).enqueueCancel("M1", "oro-clerk", "撤销配发");
        verify(repository).updateDispatchBatch(batch);
        verify(repository, never()).updateDispatchBag(any());
        var handoff = new HandoffCreateRequest(); handoff.setBagNo("P1"); handoff.setToFacilityCode("LN-OR-RC");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.createHandoffRecord(handoff)).getCode());
        var link = new com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest(); link.setTransportTaskCode("T1");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.linkTransportTask(7L, link)).getCode());
        var arrived = new com.liana.post.common.dto.sorting.ManifestArrivedRequest(); arrived.setManifestCode("M1"); arrived.setTotalPackageNo("P1");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.markManifestArrived(arrived)).getCode());
        verify(repository, never()).saveHandoffRecord(any());
        verify(repository, never()).linkTransportTask(any(), any());
    }

    @Test
    void cancellationRejectsOtherFacilityHandoffFactsAndLinkedTaskWithoutEnqueuing() {
        var batch = batch("D1", "P1", List.of("P1")); batch.setManifestNo("M1");
        when(repository.findDispatchBatchByBatchNo("D1")).thenReturn(Optional.of(batch));
        var bag = bag("P1", "LN-NAM-HQ", "LN-OR-RC");
        when(repository.lockDispatchBagByBagNo("P1")).thenReturn(Optional.of(bag));
        var request = new com.liana.post.dispatch.model.dto.DispatchBatchCancelRequest("cancel");
        assertEquals(403, assertThrows(BusinessException.class, () -> service.cancelDispatchBatch("D1", request)).getCode());
        bag.setOriginFacilityCode("LN-OR-01"); bag.setTransportTaskCode("T1");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.cancelDispatchBatch("D1", request)).getCode());
        bag.setTransportTaskCode(null);
        when(repository.findHandoffRecordByBagNo("P1")).thenReturn(Optional.of(new com.liana.post.dispatch.model.entity.HandoffRecordEntity()));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.cancelDispatchBatch("D1", request)).getCode());
        verify(outbox, never()).enqueueCancel(any(), any(), any());
        verify(repository, never()).updateDispatchBatch(any());
    }

    @Test
    void cancellationWaitsForConfirmedPrealertAndRollsBackWhenQueuePreparationFails() {
        var batch = batch("D1", "P1", List.of("P1")); batch.setManifestNo("M1");
        when(repository.findDispatchBatchByBatchNo("D1")).thenReturn(Optional.of(batch));
        when(repository.lockDispatchBagByBagNo("P1")).thenReturn(Optional.of(bag("P1", "LN-OR-01", "LN-OR-RC")));
        org.mockito.Mockito.doThrow(new BusinessException(409, "prealert unconfirmed")).when(outbox).enqueueCancel("M1", "oro-clerk", "cancel");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.cancelDispatchBatch("D1",
                new com.liana.post.dispatch.model.dto.DispatchBatchCancelRequest("cancel"))).getCode());
        assertEquals("PENDING", batch.getStatus());
        verify(repository, never()).updateDispatchBatch(any());
    }

    private DispatchBatchEntity batch(String batchNo, String bagNo, List<String> bagNos) {
        DispatchBatchEntity batch = new DispatchBatchEntity();
        batch.setBatchNo(batchNo);
        batch.setBagNo(bagNo);
        batch.setBagNos(bagNos);
        batch.setStatus("PENDING");
        return batch;
    }
}
