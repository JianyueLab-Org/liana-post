package com.liana.post.oms.service.impl;

import com.liana.post.common.constant.LogisticsConstants;
import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.UpuBarcodeUtil;
import com.liana.post.oms.cache.RedisCacheSupport;
import com.liana.post.oms.cache.RedisKeyConstants;
import com.liana.post.oms.client.DispatchClient;
import com.liana.post.oms.client.TrackingClient;
import com.liana.post.oms.constant.OmsConstants;
import com.liana.post.oms.model.dto.CountryResponse;
import com.liana.post.oms.model.dto.MailBagAssignRequest;
import com.liana.post.oms.model.dto.MailCreateRequest;
import com.liana.post.oms.model.dto.MailPackageSummaryResponse;
import com.liana.post.oms.model.dto.MailRouteAssignRequest;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.oms.model.dto.MailSlotSealRequest;
import com.liana.post.oms.model.dto.MailSlotSealResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import com.liana.post.oms.model.dto.MailStatusUpdateRequest;
import com.liana.post.oms.model.dto.ServiceTypeResponse;
import com.liana.post.oms.model.entity.MailEntity;
import com.liana.post.oms.model.entity.RecipientEntity;
import com.liana.post.oms.model.entity.SenderEntity;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.oms.repository.OmsRepository;
import com.liana.post.oms.service.OmsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OmsServiceImpl implements OmsService {
    private static final Duration MAIL_TTL = Duration.ofMinutes(10);
    private static final Duration CANDIDATE_TTL = Duration.ofMinutes(5);

    private final OmsRepository omsRepository;
    private final ObjectProvider<DispatchClient> dispatchClientProvider;
    private final ObjectProvider<TrackingClient> trackingClientProvider;
    private final StringRedisTemplate redisTemplate;

    public OmsServiceImpl(OmsRepository omsRepository, ObjectProvider<DispatchClient> dispatchClientProvider, ObjectProvider<TrackingClient> trackingClientProvider, StringRedisTemplate redisTemplate) {
        this.omsRepository = omsRepository;
        this.dispatchClientProvider = dispatchClientProvider;
        this.trackingClientProvider = trackingClientProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public MailResponse createMail(MailCreateRequest request) {
        omsRepository.seedDefaults();

        String facilityCode = resolveFacilityCode(request);
        String mailScope = normalizeMailScope(request.getMailScope());

        SenderEntity sender = new SenderEntity();
        sender.setFullName(request.getSenderFullName());
        sender.setPhone(request.getSenderPhone());
        sender.setIdType(request.getSenderIdType());
        sender.setIdNumber(request.getSenderIdNumber());
        sender.setAddress(request.getSenderAddress());
        sender.setPostcode(request.getSenderPostcode());
        sender = omsRepository.saveSender(sender);

        RecipientEntity recipient = new RecipientEntity();
        recipient.setFullName(request.getRecipientFullName());
        recipient.setPhone(request.getRecipientPhone());
        recipient.setAddress(request.getRecipientAddress());
        recipient.setPostcode(request.getRecipientPostcode());
        recipient = omsRepository.saveRecipient(recipient);

        String mailTypeCode = normalizeMailTypeCode(request.getMailTypeCode());
        if (omsRepository.findMailTypeByCode(mailTypeCode).isEmpty()) {
            throw new BusinessException(400, "unsupported mail type code");
        }
        if (omsRepository.findServiceTypeByCode(request.getServiceType()).isEmpty()) {
            throw new BusinessException(400, "unsupported service type");
        }

        MailEntity mail = new MailEntity();
        mail.setWaybillNo(UpuBarcodeUtil.generateRandom(serviceIndicatorForMailType(mailTypeCode)));
        mail.setMailTypeCode(mailTypeCode);
        mail.setServiceType(request.getServiceType().trim().toUpperCase());
        mail.setMailScope(mailScope);
        mail.setDestCountryCode(resolveDestCountryCode(mailScope, request.getDestCountryCode()));
        mail.setSenderId(sender.getId());
        mail.setRecipientId(recipient.getId());
        mail.setOriginFacilityCode(facilityCode);
        mail.setCurrentFacilityCode(facilityCode);
        mail.setWeightGrams(request.getWeightGrams());
        mail.setDeclaredValue(request.getDeclaredValue());
        mail.setStatus(LogisticsConstants.MAIL_STATUS_CREATED);
        mail = omsRepository.saveMail(mail);
        recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_ACCEPTED, facilityCode, null, null, "{}");
        evictMailCache(mail.getWaybillNo());
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    public MailResponse getMailByBarcode(String barcode) {
        return toResponse(findMailOrThrow(barcode));
    }

    @Override
    public MailResponse getMailByWaybillNo(String waybillNo) {
        String cacheKey = RedisKeyConstants.OMS_MAIL_BY_WAYBILL_PREFIX + normalize(waybillNo);
        return RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, MailResponse.class, MAIL_TTL, () -> toResponse(findMailOrThrow(waybillNo)));
    }

    @Override
    public List<MailResponse> listMails() {
        return omsRepository.findAllMails().stream().map(this::toResponse).toList();
    }

    @Override
    public List<MailResponse> listMailsByStatus(String status) {
        String cacheKey = RedisKeyConstants.OMS_MAIL_LIST_BY_STATUS_PREFIX + normalize(status);
        return RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, List.class, MAIL_TTL,
                () -> omsRepository.findMailsByStatus(status).stream().map(this::toResponse).toList());
    }

    @Override
    public List<CountryResponse> listCountries() {
        return omsRepository.findAllCountries().stream().map(com.liana.post.oms.util.OmsMapper::toCountryResponse).toList();
    }

    @Override
    public List<ServiceTypeResponse> listServiceTypes() {
        return omsRepository.findAllServiceTypes().stream().map(com.liana.post.oms.util.OmsMapper::toServiceTypeResponse).toList();
    }

    @Override
    public List<ServiceTypeResponse> listServiceTypesByCountry(String countryCode) {
        return omsRepository.findServiceTypesByCountryCode(countryCode).stream().map(com.liana.post.oms.util.OmsMapper::toServiceTypeResponse).toList();
    }

    @Override
    public List<MailPackageSummaryResponse> listPackages() {
        return omsRepository.findPackageSummaries();
    }

    @Override
    public List<MailResponse> listPendingDeliveryMails(String currentFacilityCode) {
        return omsRepository.findPendingDeliveryMails(currentFacilityCode).stream().map(this::toResponse).toList();
    }

    @Override
    public List<MailDispatchCandidateResponse> listDispatchCandidates(MailDispatchCandidateQueryRequest request) {
        String cacheKey = RedisKeyConstants.OMS_DISPATCH_CANDIDATES_PREFIX
                + safeKey(request == null ? null : request.getCurrentFacilityCode()) + ":"
                + safeKey(request == null ? null : request.getDestFacilityCode()) + ":"
                + safeKey(request == null ? null : request.getMailTypeCode()) + ":"
                + safeKey(request == null ? null : request.getMailScope()) + ":"
                + safeKey(request == null ? null : request.getDestCountryCode());
        return RedisCacheSupport.getOrLoad(redisTemplate, cacheKey, new com.fasterxml.jackson.core.type.TypeReference<List<MailDispatchCandidateResponse>>() {}, CANDIDATE_TTL,
                () -> omsRepository.findAllMails().stream()
                        .filter(mail -> request == null || matches(mail.getCurrentFacilityCode(), request.getCurrentFacilityCode()))
                        .filter(mail -> request == null || matches(mail.getDestFacilityCode(), request.getDestFacilityCode()))
                        .filter(mail -> request == null || matches(mail.getMailTypeCode(), request.getMailTypeCode()))
                        .filter(mail -> request == null || matches(normalizeMailScope(mail.getMailScope()), request.getMailScope()))
                        .filter(mail -> request == null || matches(mail.getDestCountryCode(), request.getDestCountryCode()))
                        .filter(mail -> LogisticsConstants.MAIL_STATUS_CREATED.equals(mail.getStatus()))
                        .filter(mail -> mail.getBagNo() == null || mail.getBagNo().isBlank())
                        .map(mail -> {
                            MailDispatchCandidateResponse response = new MailDispatchCandidateResponse();
                            response.setWaybillNo(mail.getWaybillNo());
                            response.setMailTypeCode(mail.getMailTypeCode());
                            response.setMailScope(normalizeMailScope(mail.getMailScope()));
                            response.setStatus(mail.getStatus());
                            response.setCurrentFacilityCode(mail.getCurrentFacilityCode());
                            response.setDestFacilityCode(mail.getDestFacilityCode());
                            response.setDestCountryCode(mail.getDestCountryCode());
                            response.setWeightGrams(mail.getWeightGrams());
                            return response;
                        }).toList());
    }

    @Override
    @Transactional
    public MailResponse updateMailStatus(String waybillNo, MailStatusUpdateRequest request) {
        MailEntity mail = omsRepository.updateMailStatus(waybillNo, request.getStatus(), request.getCurrentFacilityCode());
        recordTracking(mail.getWaybillNo(), mapStatusToEvent(mail.getStatus()), mail.getCurrentFacilityCode(), null, null, "{}");
        evictMailCache(waybillNo);
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    @Transactional
    public MailResponse assignMailBag(String waybillNo, MailBagAssignRequest request) {
        MailEntity mail = omsRepository.assignMailBag(waybillNo, request.getBagNo(), request.getCurrentFacilityCode());
        syncBagToDispatch(request.getBagNo(), request.getCurrentFacilityCode(), List.of(waybillNo));
        evictMailCache(waybillNo);
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    @Transactional
    public int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode) {
        int updated = omsRepository.assignMailBagToWaybillNos(waybillNos, bagNo, currentFacilityCode);
        syncBagToDispatch(bagNo, currentFacilityCode, waybillNos);
        for (String waybillNo : waybillNos) {
            evictMailCache(waybillNo);
        }
        evictCandidateCache();
        return updated;
    }

    @Override
    @Transactional
    public MailResponse updateMailRoute(String waybillNo, MailRouteAssignRequest request) {
        MailEntity mail = omsRepository.assignMailSlot(waybillNo, request.getCurrentSlot(), request.getDestinationNode(), request.getCurrentFacilityCode(), request.getStatus());
        recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_SORTED, mail.getCurrentFacilityCode(), null, null,
                "{\"slot\":\"" + safeJson(mail.getCurrentSlot()) + "\",\"destinationNode\":\"" + safeJson(mail.getDestinationNode()) + "\"}");
        evictMailCache(waybillNo);
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    @Transactional
    public int receiveAndOpenMailPackage(String packageId, String currentFacilityCode) {
        List<MailEntity> packageMails = omsRepository.findMailsByPackageId(packageId);
        int updated = omsRepository.receiveAndOpenMailPackage(packageId, currentFacilityCode);
        if (updated > 0) {
            packageMails.forEach(mail ->
                    recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_ARRIVED, currentFacilityCode, null, null, "{\"packageId\":\"" + safeJson(packageId) + "\"}"));
        }
        evictCandidateCache();
        return updated;
    }

    @Override
    public List<MailSlotSummaryResponse> listActiveSlots() {
        return omsRepository.findActiveSlots();
    }

    @Override
    @Transactional
    public MailSlotSealResponse sealMailSlot(String slotCode, MailSlotSealRequest request) {
        List<MailEntity> slotMails = omsRepository.findAllMails().stream()
                .filter(mail -> slotCode != null && slotCode.equalsIgnoreCase(mail.getCurrentSlot()))
                .toList();
        MailSlotSealResponse response = omsRepository.sealMailSlot(slotCode, request.getPackageId(), request.getDestinationNode(), request.getCurrentFacilityCode(), request.getStatus());
        if (response != null && response.getUpdatedCount() != null && response.getUpdatedCount() > 0) {
            slotMails.forEach(mail -> recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_DISPATCHED, request.getCurrentFacilityCode(), null, null,
                    "{\"slotCode\":\"" + safeJson(slotCode) + "\",\"destinationNode\":\"" + safeJson(response.getDestinationNode()) + "\"}"));
        }
        evictCandidateCache();
        return response;
    }

    @Override
    @Transactional
    public MailResponse deliverMail(String waybillNo, String facilityCode) {
        MailEntity mail = omsRepository.updateMailStatus(waybillNo, LogisticsConstants.MAIL_STATUS_DELIVERED, facilityCode);
        recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_DELIVERED, mail.getCurrentFacilityCode(), null, null, "{}");
        evictMailCache(waybillNo);
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    @Transactional
    public MailResponse departExchangeMail(String waybillNo, String facilityCode) {
        MailEntity mail = omsRepository.updateMailStatus(waybillNo, LogisticsConstants.MAIL_STATUS_DISPATCHED, facilityCode);
        recordTracking(mail.getWaybillNo(), LogisticsConstants.TRACKING_EVENT_DISPATCHED, mail.getCurrentFacilityCode(), null, null, "{}");
        evictMailCache(waybillNo);
        evictCandidateCache();
        return toResponse(mail);
    }

    @Override
    public void initDefaults() {
        omsRepository.seedDefaults();
    }

    @Override
    public DashboardSummaryResponse dashboardSummary(String facilityCode) {
        List<MailResponse> mails = listMails().stream()
                .filter(mail -> facilityCode == null || facilityCode.isBlank() || matches(mail.getCurrentFacilityCode(), facilityCode) || matches(mail.getOriginFacilityCode(), facilityCode))
                .toList();
        List<MailPackageSummaryResponse> packages = listPackages().stream()
                .filter(pack -> facilityCode == null || facilityCode.isBlank() || matches(pack.getCurrentFacilityCode(), facilityCode) || matches(pack.getOriginFacilityCode(), facilityCode))
                .toList();
        long pendingDelivery = mails.stream()
                .filter(mail -> LogisticsConstants.MAIL_STATUS_ARRIVED.equals(mail.getStatus()) || LogisticsConstants.MAIL_STATUS_SORTED.equals(mail.getStatus()))
                .count();
        long international = mails.stream().filter(mail -> "INTERNATIONAL".equals(mail.getMailScope())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("邮件数据");
        response.setScope(scopeLabel(facilityCode));
        response.addMetric("邮件总量", mails.size(), "mail 表", "info")
                .addMetric("待投递", pendingDelivery, "ARRIVED / SORTED", "warning")
                .addMetric("总包数", packages.size(), "package_id 聚合", "neutral")
                .addMetric("国际邮件", international, "mail_scope = INTERNATIONAL", "success");
        response.addBreakdown("邮件状态", countBy(mails.stream().map(MailResponse::getStatus).collect(Collectors.toList())));
        response.addBreakdown("邮件范围", List.of(
                DashboardSummaryResponse.item("国内", mails.size() - international, "DOMESTIC"),
                DashboardSummaryResponse.item("国际", international, "INTERNATIONAL")
        ));
        response.addBreakdown("待建袋邮件", pendingDispatchBreakdown(mails));
        return response;
    }

    private MailEntity findMailOrThrow(String barcode) {
        return omsRepository.findMailByWaybillNo(normalize(barcode)).orElseThrow(() -> new BusinessException(404, "mail not found"));
    }

    private void syncBagToDispatch(String bagNo, String currentFacilityCode, List<String> waybillNos) {
        if (bagNo == null || bagNo.isBlank() || waybillNos == null || waybillNos.isEmpty()) {
            return;
        }
        DispatchClient dispatchClient = dispatchClientProvider.getIfAvailable();
        if (dispatchClient == null) {
            return;
        }
        MailBagSyncRequest request = new MailBagSyncRequest();
        request.setBagNo(bagNo);
        request.setCurrentFacilityCode(currentFacilityCode);
        request.setWaybillNos(waybillNos);
        try {
            dispatchClient.syncMailBag(request);
        } catch (Exception ignored) {
        }
    }

    private void recordTracking(String waybillNo, String eventType, String facilityCode, Long operatorId, String operatorName, String payload) {
        TrackingClient client = trackingClientProvider.getIfAvailable();
        if (client == null || waybillNo == null || eventType == null) {
            return;
        }
        TrackingEventCreateRequest request = new TrackingEventCreateRequest();
        request.setWaybillNo(waybillNo);
        request.setEventType(eventType);
        request.setSourceService("OMS");
        request.setFacilityCode(facilityCode);
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
        request.setPayload(payload);
        try {
            client.recordEvent(request);
        } catch (Exception ignored) {
        }
    }

    private String mapStatusToEvent(String status) {
        if (LogisticsConstants.MAIL_STATUS_ACCEPTED.equals(status)) {
            return LogisticsConstants.TRACKING_EVENT_ACCEPTED;
        }
        if (LogisticsConstants.MAIL_STATUS_DELIVERED.equals(status)) {
            return LogisticsConstants.TRACKING_EVENT_DELIVERED;
        }
        if (LogisticsConstants.MAIL_STATUS_DISPATCHED.equals(status)) {
            return LogisticsConstants.TRACKING_EVENT_DISPATCHED;
        }
        if (LogisticsConstants.MAIL_STATUS_ARRIVED.equals(status)) {
            return LogisticsConstants.TRACKING_EVENT_ARRIVED;
        }
        if (LogisticsConstants.MAIL_STATUS_RETURNED.equals(status)) {
            return LogisticsConstants.TRACKING_EVENT_RETURNED;
        }
        return LogisticsConstants.TRACKING_EVENT_SORTED;
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void evictMailCache(String waybillNo) {
        RedisCacheSupport.evict(redisTemplate, RedisKeyConstants.OMS_MAIL_BY_WAYBILL_PREFIX + normalize(waybillNo));
    }

    private void evictCandidateCache() {
        RedisCacheSupport.evictLike(redisTemplate, RedisKeyConstants.OMS_DISPATCH_CANDIDATES_PREFIX);
        RedisCacheSupport.evictLike(redisTemplate, RedisKeyConstants.OMS_MAIL_LIST_BY_STATUS_PREFIX);
    }

    private String resolveFacilityCode(MailCreateRequest request) {
        if (request.getCurrentFacilityCode() != null && !request.getCurrentFacilityCode().isBlank()) {
            return request.getCurrentFacilityCode().trim().toUpperCase();
        }
        if (request.getOriginFacilityCode() != null && !request.getOriginFacilityCode().isBlank()) {
            return request.getOriginFacilityCode().trim().toUpperCase();
        }
        return null;
    }

    private String resolveDestCountryCode(String mailScope, String destCountryCode) {
        if (!"INTERNATIONAL".equals(mailScope)) {
            return null;
        }
        if (destCountryCode == null || destCountryCode.isBlank()) {
            throw new BusinessException(400, "destCountryCode is required for international mail");
        }
        String normalized = destCountryCode.trim().toUpperCase();
        if (omsRepository.findCountryByCode(normalized).isEmpty()) {
            throw new BusinessException(404, "destination country not found: " + normalized);
        }
        return normalized;
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected.trim()));
    }

    private String scopeLabel(String facilityCode) {
        return facilityCode == null || facilityCode.isBlank() ? "全部机构" : facilityCode.trim().toUpperCase();
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

    private List<DashboardSummaryResponse.BreakdownItem> pendingDispatchBreakdown(List<MailResponse> mails) {
        return mails.stream()
                .filter(mail -> LogisticsConstants.MAIL_STATUS_CREATED.equals(mail.getStatus()))
                .filter(mail -> mail.getBagNo() == null || mail.getBagNo().isBlank())
                .collect(Collectors.groupingBy(this::dispatchGroupLabel, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> DashboardSummaryResponse.item(entry.getKey(), entry.getValue(), entry.getKey()))
                .toList();
    }

    private String dispatchGroupLabel(MailResponse mail) {
        String mailType = mail.getMailTypeCode() == null || mail.getMailTypeCode().isBlank() ? "UNKNOWN" : mail.getMailTypeCode();
        String scope = normalizeMailScope(mail.getMailScope());
        if ("INTERNATIONAL".equals(scope)) {
            String country = mail.getDestCountryCode() == null || mail.getDestCountryCode().isBlank() ? "UNKNOWN" : mail.getDestCountryCode().toUpperCase();
            return mailType + " / INTERNATIONAL / " + country;
        }
        return mailType + " / DOMESTIC";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeMailTypeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "mail type code cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    static String serviceIndicatorForMailType(String mailTypeCode) {
        String normalized = mailTypeCode == null ? "" : mailTypeCode.trim().toUpperCase();
        return switch (normalized) {
            case OmsConstants.MAIL_TYPE_PACKAGE -> "CP";
            case OmsConstants.MAIL_TYPE_REGISTERED -> "RR";
            case OmsConstants.MAIL_TYPE_EMS -> "EE";
            default -> throw new BusinessException(400, "unsupported mail type code");
        };
    }

    private String safeKey(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase();
    }

    private String normalizeMailScope(String value) {
        if (value == null || value.isBlank()) {
            return "DOMESTIC";
        }
        String normalized = value.trim().toUpperCase();
        if (!"DOMESTIC".equals(normalized) && !"INTERNATIONAL".equals(normalized)) {
            throw new BusinessException(400, "unsupported mail scope");
        }
        return normalized;
    }

    private MailResponse toResponse(MailEntity mail) {
        MailResponse response = new MailResponse();
        response.setId(mail.getId());
        response.setWaybillNo(mail.getWaybillNo());
        response.setBagNo(mail.getBagNo());
        response.setPackageId(mail.getPackageId());
        response.setMailTypeCode(mail.getMailTypeCode());
        response.setServiceType(mail.getServiceType());
        response.setMailScope(normalizeMailScope(mail.getMailScope()));
        response.setDestCountryCode(mail.getDestCountryCode());
        response.setStatus(mail.getStatus());
        response.setOriginFacilityCode(mail.getOriginFacilityCode());
        response.setCurrentFacilityCode(mail.getCurrentFacilityCode());
        response.setCurrentSlot(mail.getCurrentSlot());
        response.setDestFacilityCode(mail.getDestFacilityCode());
        response.setDestinationNode(mail.getDestinationNode());
        response.setWeightGrams(mail.getWeightGrams());
        response.setDeclaredValue(mail.getDeclaredValue());
        response.setSenderFullName(loadSenderName(mail.getSenderId()));
        response.setRecipientFullName(loadRecipientName(mail.getRecipientId()));
        return response;
    }

    private String loadSenderName(Long senderId) {
        if (senderId == null) {
            return null;
        }
        return omsRepository.findSenderById(senderId).map(SenderEntity::getFullName).orElse(null);
    }

    private String loadRecipientName(Long recipientId) {
        if (recipientId == null) {
            return null;
        }
        return omsRepository.findRecipientById(recipientId).map(RecipientEntity::getFullName).orElse(null);
    }
}
