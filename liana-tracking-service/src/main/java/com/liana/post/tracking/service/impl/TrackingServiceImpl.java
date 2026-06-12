package com.liana.post.tracking.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.UpuBarcodeUtil;
import com.liana.post.tracking.cache.RedisCacheSupport;
import com.liana.post.tracking.cache.RedisKeyConstants;
import com.liana.post.tracking.client.DispatchClient;
import com.liana.post.tracking.client.OMSClient;
import com.liana.post.tracking.constant.TrackingConstants;
import com.liana.post.tracking.model.dto.TrackingEventResponse;
import com.liana.post.tracking.model.dto.TrackingQueryRequest;
import com.liana.post.tracking.model.entity.TrackingEventEntity;
import com.liana.post.tracking.repository.TrackingRepository;
import com.liana.post.tracking.service.TrackingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TrackingServiceImpl implements TrackingService {
    private static final Duration EVENT_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TrackingRepository trackingRepository;
    private final ObjectProvider<OMSClient> omsClientProvider;
    private final ObjectProvider<DispatchClient> dispatchClientProvider;
    private final StringRedisTemplate redisTemplate;

    public TrackingServiceImpl(TrackingRepository trackingRepository, ObjectProvider<OMSClient> omsClientProvider, ObjectProvider<DispatchClient> dispatchClientProvider, StringRedisTemplate redisTemplate) {
        this.trackingRepository = trackingRepository;
        this.omsClientProvider = omsClientProvider;
        this.dispatchClientProvider = dispatchClientProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public TrackingEventResponse recordEvent(TrackingEventCreateRequest request) {
        TrackingEventEntity entity = new TrackingEventEntity();
        entity.setEventNo(com.liana.post.common.util.IdGeneratorUtil.generateTrackingEventNo());
        entity.setWaybillNo(normalize(request.getWaybillNo()));
        entity.setEventType(normalizeEventType(request.getEventType()));
        entity.setSourceService(normalizeSource(request.getSourceService()));
        entity.setFacilityCode(request.getFacilityCode());
        entity.setFacilityName(request.getFacilityName());
        entity.setOperatorId(request.getOperatorId());
        entity.setOperatorName(request.getOperatorName());
        entity.setPayload(request.getPayload());
        entity.setEventTime(LocalDateTime.now());
        trackingRepository.save(entity);
        evictTrackCaches(entity.getWaybillNo(), entity.getEventNo());
        return toResponse(entity);
    }

    @Override
    public TrackingEventResponse getByEventNo(String eventNo) {
        String cacheKey = RedisKeyConstants.TRACKING_EVENT_BY_NO_PREFIX + normalize(eventNo);
        return RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, TrackingEventResponse.class, EVENT_TTL,
                () -> toResponse(trackingRepository.findByEventNo(eventNo).orElseThrow(() -> new BusinessException(404, "tracking event not found"))));
    }

    @Override
    public List<TrackingEventResponse> listEvents() {
        return trackingRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<TrackingEventResponse> listByWaybillNo(String waybillNo) {
        String cacheKey = RedisKeyConstants.TRACKING_EVENT_BY_WAYBILL_PREFIX + normalize(waybillNo);
        return RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, new TypeReference<List<TrackingEventResponse>>() {}, EVENT_TTL,
                () -> trackingRepository.findByWaybillNo(waybillNo).stream().map(this::toResponse).toList());
    }

    @Override
    public List<TrackingEventResponse> search(TrackingQueryRequest request) {
        if (request == null || request.getWaybillNo() == null || request.getWaybillNo().isBlank()) {
            throw new BusinessException(400, "waybillNo cannot be blank");
        }
        return trackingRepository.findByWaybillNoAndFilters(request.getWaybillNo(), request.getEventType(), request.getSourceService(), request.getFacilityCode())
                .stream().map(this::toResponse).toList();
    }

    @Override
    public void initDefaults() {
        trackingRepository.seedDefaults();
    }

    public List<TrackingEventResponse> listOmsCandidates(MailDispatchCandidateQueryRequest request) {
        Result<List<MailDispatchCandidateResponse>> result = omsClientProvider.getObject().listDispatchCandidates(request);
        if (result == null || result.getData() == null) {
            return List.of();
        }
        return result.getData().stream().map(candidate -> {
            TrackingEventResponse response = new TrackingEventResponse();
            response.setWaybillNo(candidate.getWaybillNo());
            response.setEventType(TrackingConstants.EVENT_ACCEPTED);
            response.setSourceService(TrackingConstants.SOURCE_OMS);
            response.setFacilityCode(candidate.getCurrentFacilityCode());
            response.setPayload("{}");
            return response;
        }).toList();
    }

    public DispatchBagBriefResponse syncDispatchBag(MailBagSyncRequest request) {
        Result<DispatchBagBriefResponse> result = dispatchClientProvider.getObject().syncMailBag(request);
        return result == null ? null : result.getData();
    }

    private void evictTrackCaches(String waybillNo, String eventNo) {
        RedisCacheSupport.evict(redisTemplate, RedisKeyConstants.TRACKING_EVENT_BY_WAYBILL_PREFIX + normalize(waybillNo));
        RedisCacheSupport.evict(redisTemplate, RedisKeyConstants.TRACKING_EVENT_BY_NO_PREFIX + normalize(eventNo));
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException(400, "eventType cannot be blank");
        }
        String normalized = eventType.trim().toUpperCase();
        return switch (normalized) {
            case TrackingConstants.EVENT_ACCEPTED,
                    TrackingConstants.EVENT_SORTED,
                    TrackingConstants.EVENT_DISPATCHED,
                    TrackingConstants.EVENT_ARRIVED,
                    TrackingConstants.EVENT_DELIVERED,
                    TrackingConstants.EVENT_RETURNED,
                    TrackingConstants.EVENT_TRANSPORT_ASSIGNED,
                    TrackingConstants.EVENT_TRANSPORT_DEPARTED,
                    TrackingConstants.EVENT_TRANSPORT_IN_TRANSIT,
                    TrackingConstants.EVENT_TRANSPORT_ARRIVED_AT_HUB,
                    TrackingConstants.EVENT_TRANSPORT_COMPLETED -> normalized;
            default -> throw new BusinessException(400, "unsupported tracking event type");
        };
    }

    private String normalizeSource(String sourceService) {
        if (sourceService == null || sourceService.isBlank()) {
            throw new BusinessException(400, "sourceService cannot be blank");
        }
        return sourceService.trim().toUpperCase();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    private TrackingEventResponse toResponse(TrackingEventEntity entity) {
        TrackingEventResponse response = new TrackingEventResponse();
        response.setEventNo(entity.getEventNo());
        response.setWaybillNo(entity.getWaybillNo());
        response.setEventType(entity.getEventType());
        response.setEventTime(entity.getEventTime() == null ? null : entity.getEventTime().format(FORMATTER));
        response.setFacilityCode(entity.getFacilityCode());
        response.setFacilityName(entity.getFacilityName());
        response.setOperatorId(entity.getOperatorId());
        response.setOperatorName(entity.getOperatorName());
        response.setPayload(entity.getPayload());
        response.setSourceService(entity.getSourceService());
        return response;
    }
}
