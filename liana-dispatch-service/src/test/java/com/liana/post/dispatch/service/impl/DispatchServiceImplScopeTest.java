package com.liana.post.dispatch.service.impl;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.dispatch.client.OmsClient;
import com.liana.post.dispatch.client.FacilityRouteClient;
import com.liana.post.dispatch.client.SortingManifestClient;
import com.liana.post.dispatch.model.dto.DispatchBatchCreateRequest;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
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

class DispatchServiceImplScopeTest {

    private DispatchRepository repository;
    private HttpServletRequest httpRequest;
    private DispatchServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(DispatchRepository.class);
        httpRequest = mock(HttpServletRequest.class);
        ObjectProvider<OmsClient> omsClientProvider = mock(ObjectProvider.class);
        ObjectProvider<SortingManifestClient> sortingClientProvider = mock(ObjectProvider.class);
        ObjectProvider<FacilityRouteClient> facilityRouteClientProvider = mock(ObjectProvider.class);
        service = new DispatchServiceImpl(repository, omsClientProvider, httpRequest, sortingClientProvider, facilityRouteClientProvider, "http://localhost:8087");
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

    private DispatchBagEntity bag(String bagNo, String origin, String destination) {
        DispatchBagEntity bag = new DispatchBagEntity();
        bag.setBagNo(bagNo);
        bag.setOriginFacilityCode(origin);
        bag.setDestinationFacilityCode(destination);
        return bag;
    }

    private DispatchBatchEntity batch(String batchNo, String bagNo, List<String> bagNos) {
        DispatchBatchEntity batch = new DispatchBatchEntity();
        batch.setBatchNo(batchNo);
        batch.setBagNo(bagNo);
        batch.setBagNos(bagNos);
        return batch;
    }
}
