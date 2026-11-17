package com.liana.post.transport.service.impl;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.transport.model.dto.TransportScheduleRequest;
import com.liana.post.transport.model.entity.TransportAssetEntity;
import com.liana.post.transport.model.entity.TransportRouteEntity;
import com.liana.post.transport.repository.TransportRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransportScheduleBusinessDayTest {
    @ParameterizedTest
    @CsvSource({"2030-01-07T11:59:00,MON,true", "2030-01-07T12:01:00,TUE,true",
            "2030-01-07T12:01:00,MON,false", "2030-01-07T11:59:00,TUE,false"})
    void scheduleUsesIslandWeekdayAcrossUtcNoon(String departure, String weekday, boolean valid) {
        var repository = mock(TransportRepository.class);
        var service = new TransportServiceImpl(repository, null, null, null, null, null);
        var route = new TransportRouteEntity();
        route.setId(1L); route.setTransportType("SEA"); route.setStatus("ACTIVE");
        var asset = new TransportAssetEntity();
        asset.setId(2L); asset.setType("SHIP"); asset.setStatus("AVAILABLE");
        when(repository.findRouteById(1L)).thenReturn(Optional.of(route));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(asset));
        var request = new TransportScheduleRequest();
        request.setScheduleCode("ISLAND-DAY"); request.setRouteId(1L); request.setAssetId(2L);
        request.setDepartureTime(LocalDateTime.parse(departure));
        request.setArrivalTime(request.getDepartureTime().plusHours(1));
        request.setWeekday(weekday); request.setStatus("SUSPENDED");
        if (valid) {
            service.createSchedule(request);
            verify(repository).saveSchedule(argThat(s -> s.getDepartureTime().equals(request.getDepartureTime())
                    && s.getWeekday().equals(weekday)));
        } else {
            assertThrows(BusinessException.class, () -> service.createSchedule(request));
            verify(repository, never()).saveSchedule(any());
        }
    }
}
