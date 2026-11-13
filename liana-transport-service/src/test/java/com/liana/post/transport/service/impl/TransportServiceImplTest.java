package com.liana.post.transport.service.impl;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.model.Result;
import com.liana.post.transport.client.DispatchClient;
import com.liana.post.transport.client.FacilityRouteClient;
import com.liana.post.transport.client.TrackingClient;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import com.liana.post.transport.model.entity.TransportTaskEntity;
import com.liana.post.transport.repository.TransportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransportServiceImplTest {
    @Test
    void oroReturnUsesFacilityEdgeAndScheduledAircraftInsteadOfRuleCode() {
        TransportRepository repository = mock(TransportRepository.class);
        DispatchClient dispatch = mock(DispatchClient.class);
        @SuppressWarnings("unchecked") ObjectProvider<DispatchClient> dispatchProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<TrackingClient> trackingProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<FacilityRouteClient> facilityProvider = mock(ObjectProvider.class);
        when(dispatchProvider.getIfAvailable()).thenReturn(dispatch);
        DispatchBagBriefResponse bag = new DispatchBagBriefResponse();
        bag.setDispatchBagId(7L);
        bag.setBagNo("B-ORO-7");
        bag.setOriginFacilityCode("LN-OR-RC");
        bag.setDestinationFacilityCode("LN-NAM-AIR");
        bag.setRouteCode("LN-OR-RC-NAM-AIR-RULE");
        bag.setFacilityRouteCode("LN-OR-RC-AIR");
        bag.setTotalWeightGrams(500);
        when(dispatch.getBagById(7L)).thenReturn(Result.ok(bag));
        when(dispatch.linkTransportTask(eq(7L), any())).thenReturn(Result.ok(bag));
        when(repository.findTaskByDispatchBagId(7L)).thenReturn(Optional.empty());
        TransportRouteEntity route = new TransportRouteEntity();
        route.setId(3L);
        route.setFacilityRouteCode("LN-OR-RC-AIR");
        route.setOriginFacilityId("LN-OR-RC");
        route.setDestinationFacilityId("LN-NAM-AIR");
        route.setTransportType("AIR");
        route.setStatus("ACTIVE");
        when(repository.findRoutesByFacilityRouteCode("LN-OR-RC-AIR")).thenReturn(List.of(route));
        when(repository.findRouteById(3L)).thenReturn(Optional.of(route));
        TransportAssetEntity aircraft = new TransportAssetEntity();
        aircraft.setId(4L);
        aircraft.setType("AIRCRAFT");
        aircraft.setStatus("AVAILABLE");
        aircraft.setCapacity(new BigDecimal("1800"));
        when(repository.findAssetById(4L)).thenReturn(Optional.of(aircraft));
        TransportScheduleEntity schedule = new TransportScheduleEntity();
        schedule.setId(5L);
        schedule.setRouteId(3L);
        schedule.setAssetId(4L);
        schedule.setStatus("ACTIVE");
        schedule.setDepartureTime(LocalDateTime.now().plusDays(1));
        when(repository.findFutureSchedulesByRouteId(3L)).thenReturn(List.of(schedule));
        when(repository.lockSchedule(5L)).thenReturn(schedule);
        when(repository.saveTask(any())).thenAnswer(invocation -> {
            TransportTaskEntity task = invocation.getArgument(0);
            task.setId(9L);
            return task;
        });
        TransportServiceImpl service = new TransportServiceImpl(repository, dispatchProvider, trackingProvider, facilityProvider);
        TransportTaskEntity task = service.createTaskFromDispatchBag(7L);
        assertEquals(5L, task.getScheduleId());
        assertEquals(4L, task.getAssetId());
        assertEquals("ASSIGNED", task.getStatus());
        verify(repository).findRoutesByFacilityRouteCode("LN-OR-RC-AIR");
        verify(repository).addTaskBag(argThat(link -> link.getTaskId().equals(9L) && link.getDispatchBagId().equals(7L)));
        verify(repository, never()).findRouteByCode("LN-OR-RC-NAM-AIR-RULE");
    }
}
