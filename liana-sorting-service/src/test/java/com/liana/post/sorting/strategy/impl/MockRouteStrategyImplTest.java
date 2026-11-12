package com.liana.post.sorting.strategy.impl;

import com.liana.post.common.model.Result;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.sorting.client.FacilityClient;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.sorting.model.dto.FacilityRouteResponse;
import com.liana.post.sorting.repository.SortingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockRouteStrategyImplTest {
    private final SortingRepository repository = mock(SortingRepository.class);
    private final OmsClient oms = mock(OmsClient.class);
    private final FacilityClient facility = mock(FacilityClient.class);
    private final MockRouteStrategyImpl strategy = new MockRouteStrategyImpl(repository, oms, facility);

    @Test
    void selectsFirstHopOfActiveDirectedRoute() {
        when(oms.getMail("UU123456789LN")).thenReturn(Result.ok(mail("LN-NM-01", "2000")));
        when(facility.listRoutes()).thenReturn(Result.ok(List.of(
                route("LOCAL-HQ", "LN-NM-01", "LN-NAM-HQ", 1),
                route("HQ-AIR", "LN-NAM-HQ", "LN-NAM-AIR", 1),
                route("AIR-OR", "LN-NAM-AIR", "LN-OR-RC", 1))));

        var result = strategy.doRoute("UU123456789LN", "LN-NM-01");
        assertEquals("LN-NAM-HQ", result.getNextHopCode());
        assertEquals("LOCAL-HQ", result.getRouteCode());
        assertEquals("LN-NAM-HQ-SLOT", result.getSlotCode());
    }

    @Test
    void inactiveRouteOrMissingPostcodeRequiresManualReview() {
        when(oms.getMail("UU123456789LN")).thenReturn(Result.ok(mail("LN-NM-01", "2000")));
        when(facility.listRoutes()).thenReturn(Result.ok(List.of(route("LOCAL-HQ", "LN-NM-01", "LN-NAM-HQ", 0))));
        var noRoute = strategy.doRoute("UU123456789LN", "LN-NM-01");
        assertEquals("MANUAL_REVIEW", noRoute.getSlotCode());
        assertNull(noRoute.getNextHopCode());

        when(oms.getMail("UU123456789LN")).thenReturn(Result.ok(mail("LN-NM-01", null)));
        assertEquals("MANUAL_REVIEW", strategy.doRoute("UU123456789LN", "LN-NM-01").getSlotCode());
    }

    @Test
    void manualNextHopMustBeAnActiveEdgeFromCurrentFacility() {
        when(oms.getMail("UU123456789LN")).thenReturn(Result.ok(mail("LN-NM-01", "2000")));
        when(facility.listRoutes()).thenReturn(Result.ok(List.of(
                route("LOCAL-HQ", "LN-NM-01", "LN-NAM-HQ", 1),
                route("DISABLED", "LN-NM-01", "LN-OR-RC", 0))));
        var allowed = strategy.doManualRoute("UU123456789LN", "LN-NM-01", "LN-NAM-HQ");
        assertEquals("LN-NAM-HQ", allowed.getNextHopCode());
        assertEquals("LOCAL-HQ", allowed.getRouteCode());
        assertEquals("MANUAL_REVIEW", strategy.doManualRoute("UU123456789LN", "LN-NM-01", "LN-OR-RC").getSlotCode());
    }

    private MailResponse mail(String current, String postcode) {
        MailResponse response = new MailResponse();
        response.setCurrentFacilityCode(current);
        response.setRecipientPostcode(postcode);
        response.setMailScope("DOMESTIC");
        return response;
    }

    private FacilityRouteResponse route(String code, String origin, String destination, int status) {
        FacilityRouteResponse response = new FacilityRouteResponse();
        response.setRouteCode(code);
        response.setOriginFacilityCode(origin);
        response.setDestinationFacilityCode(destination);
        response.setTransportMode("LAND");
        response.setStatus(status);
        return response;
    }
}
