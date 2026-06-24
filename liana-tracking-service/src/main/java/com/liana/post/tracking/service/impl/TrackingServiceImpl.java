package com.liana.post.tracking.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

@Service
public class TrackingServiceImpl implements TrackingService {
    private static final Duration EVENT_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            enrichDisplayFields(response);
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
        enrichDisplayFields(response);
        return response;
    }

    private void enrichDisplayFields(TrackingEventResponse response) {
        response.setStageName(stageName(response.getEventType()));
        response.setLocationText(locationText(response.getFacilityName(), response.getFacilityCode()));
        response.setDisplayText(displayText(response));
    }

    private String displayText(TrackingEventResponse event) {
        String location = event.getLocationText();
        Map<String, Object> payload = parsePayload(event.getPayload());
        String destination = firstPayloadText(payload, "destinationNode", "nextHopCode", "targetCenterCode", "routeCode");
        String packageId = firstPayloadText(payload, "packageId", "packageNo", "bagNo", "slotCode");
        String countryCode = firstPayloadText(payload, "countryCode");

        return switch (event.getEventType() == null ? "" : event.getEventType()) {
            case TrackingConstants.EVENT_ACCEPTED -> "邮件已在" + location + "收寄，已进入寄递处理流程。";
            case TrackingConstants.EVENT_SORTED -> destination == null
                    ? "邮件已在" + location + "完成分拣处理。"
                    : "邮件已在" + location + "完成分拣，下一站：" + destination + "。";
            case TrackingConstants.EVENT_DISPATCHED -> countryCode != null
                    ? packageId == null
                    ? "邮件已在" + location + "完成出口封袋，目的国：" + countryCode + "。"
                    : "邮件已在" + location + "完成出口封袋，出口总包：" + packageId + "，目的国：" + countryCode + "。"
                    : destination == null
                    ? "邮件已从" + location + "发出，正在运输途中。"
                    : "邮件已从" + location + "发出，发往" + destination + "。";
            case TrackingConstants.EVENT_ARRIVED -> packageId == null
                    ? "邮件已到达" + location + "，等待后续处理。"
                    : "邮件已到达" + location + "，关联总包：" + packageId + "。";
            case TrackingConstants.EVENT_DELIVERED -> "邮件已在" + location + "完成投递，感谢使用。";
            case TrackingConstants.EVENT_RETURNED -> "邮件已在" + location + "退回处理。";
            case TrackingConstants.EVENT_TRANSPORT_ASSIGNED -> "邮件已在" + location + "安排运输任务。";
            case TrackingConstants.EVENT_TRANSPORT_DEPARTED -> "邮件已从" + location + "启运。";
            case TrackingConstants.EVENT_TRANSPORT_IN_TRANSIT -> "邮件正在运输途中。";
            case TrackingConstants.EVENT_TRANSPORT_ARRIVED_AT_HUB -> "邮件已到达" + location + "运输节点。";
            case TrackingConstants.EVENT_TRANSPORT_COMPLETED -> "邮件运输任务已完成，已交由下一环节处理。";
            default -> "邮件在" + location + "产生了新的处理记录。";
        };
    }

    private String stageName(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case TrackingConstants.EVENT_ACCEPTED -> "已收寄";
            case TrackingConstants.EVENT_SORTED -> "已分拣";
            case TrackingConstants.EVENT_DISPATCHED -> "已发出";
            case TrackingConstants.EVENT_ARRIVED -> "已到达";
            case TrackingConstants.EVENT_DELIVERED -> "已投递";
            case TrackingConstants.EVENT_RETURNED -> "已退回";
            case TrackingConstants.EVENT_TRANSPORT_ASSIGNED -> "已安排运输";
            case TrackingConstants.EVENT_TRANSPORT_DEPARTED -> "已启运";
            case TrackingConstants.EVENT_TRANSPORT_IN_TRANSIT -> "运输中";
            case TrackingConstants.EVENT_TRANSPORT_ARRIVED_AT_HUB -> "到达运输节点";
            case TrackingConstants.EVENT_TRANSPORT_COMPLETED -> "运输完成";
            default -> eventType == null || eventType.isBlank() ? "未知状态" : eventType;
        };
    }

    private String locationText(String facilityName, String facilityCode) {
        if (facilityName != null && !facilityName.isBlank()) {
            return facilityName.trim();
        }
        if (facilityCode != null && !facilityCode.isBlank()) {
            return facilityCode.trim();
        }
        return "处理网点";
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String firstPayloadText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
