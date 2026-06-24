package com.liana.post.oms.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liana.post.common.constant.LogisticsConstants;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.UpuBarcodeUtil;
import com.liana.post.oms.mapper.CountryMapper;
import com.liana.post.oms.mapper.CountryServiceTypeMapper;
import com.liana.post.oms.mapper.MailMapper;
import com.liana.post.oms.mapper.MailTypeMapper;
import com.liana.post.oms.mapper.RecipientMapper;
import com.liana.post.oms.mapper.SenderMapper;
import com.liana.post.oms.mapper.ServiceTypeMapper;
import com.liana.post.oms.model.dto.MailPackageSummaryResponse;
import com.liana.post.oms.model.dto.MailSlotSealResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import com.liana.post.oms.model.entity.CountryEntity;
import com.liana.post.oms.model.entity.CountryServiceTypeEntity;
import com.liana.post.oms.model.entity.MailEntity;
import com.liana.post.oms.model.entity.MailTypeEntity;
import com.liana.post.oms.model.entity.RecipientEntity;
import com.liana.post.oms.model.entity.SenderEntity;
import com.liana.post.oms.model.entity.ServiceTypeEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Repository
public class MyBatisOmsRepository implements OmsRepository {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            LogisticsConstants.MAIL_STATUS_CREATED,
            LogisticsConstants.MAIL_STATUS_ACCEPTED,
            LogisticsConstants.MAIL_STATUS_SORTED,
            LogisticsConstants.MAIL_STATUS_DISPATCHED,
            LogisticsConstants.MAIL_STATUS_ARRIVED,
            LogisticsConstants.MAIL_STATUS_READY_FOR_DELIVERY,
            LogisticsConstants.MAIL_STATUS_DELIVERED,
            LogisticsConstants.MAIL_STATUS_RETURNED
    );

    private static final Set<String> ALLOWED_MAIL_SCOPES = Set.of("DOMESTIC", "INTERNATIONAL");
    private static final Set<String> DEFAULT_SERVICE_CODES = Set.of("AIR", "SAL", "SEA");

    private final SenderMapper senderMapper;
    private final RecipientMapper recipientMapper;
    private final MailTypeMapper mailTypeMapper;
    private final MailMapper mailMapper;
    private final CountryMapper countryMapper;
    private final ServiceTypeMapper serviceTypeMapper;
    private final CountryServiceTypeMapper countryServiceTypeMapper;

    public MyBatisOmsRepository(SenderMapper senderMapper,
                                RecipientMapper recipientMapper,
                                MailTypeMapper mailTypeMapper,
                                MailMapper mailMapper,
                                CountryMapper countryMapper,
                                ServiceTypeMapper serviceTypeMapper,
                                CountryServiceTypeMapper countryServiceTypeMapper) {
        this.senderMapper = senderMapper;
        this.recipientMapper = recipientMapper;
        this.mailTypeMapper = mailTypeMapper;
        this.mailMapper = mailMapper;
        this.countryMapper = countryMapper;
        this.serviceTypeMapper = serviceTypeMapper;
        this.countryServiceTypeMapper = countryServiceTypeMapper;
    }

    @Override
    @Transactional
    public SenderEntity saveSender(SenderEntity entity) {
        entity.setCountryCode(normalizeCountry(entity.getCountryCode()));
        stamp(entity);
        if (senderMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert sender");
        }
        return entity;
    }

    @Override
    @Transactional
    public RecipientEntity saveRecipient(RecipientEntity entity) {
        entity.setCountryCode(normalizeCountry(entity.getCountryCode()));
        stamp(entity);
        if (recipientMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert recipient");
        }
        return entity;
    }

    @Override
    @Transactional
    public MailTypeEntity saveMailType(MailTypeEntity entity) {
        stamp(entity);
        if (mailTypeMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert mail type");
        }
        return entity;
    }

    @Override
    @Transactional
    public CountryEntity saveCountry(CountryEntity entity) {
        stamp(entity);
        if (countryMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert country");
        }
        return entity;
    }

    @Override
    @Transactional
    public ServiceTypeEntity saveServiceType(ServiceTypeEntity entity) {
        stamp(entity);
        if (serviceTypeMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert service type");
        }
        return entity;
    }

    @Override
    @Transactional
    public CountryServiceTypeEntity saveCountryServiceType(CountryServiceTypeEntity entity) {
        stamp(entity);
        if (countryServiceTypeMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert country service type");
        }
        return entity;
    }

    @Override
    @Transactional
    public MailEntity saveMail(MailEntity entity) {
        entity.setMailScope(normalizeMailScope(entity.getMailScope()));
        entity.setServiceType(normalizeServiceType(entity.getServiceType()));
        entity.setDestCountryCode(normalizeDestCountryCode(entity.getMailScope(), entity.getDestCountryCode()));
        if (!StringUtils.hasText(entity.getWaybillNo())) {
            entity.setWaybillNo(UpuBarcodeUtil.generateRandom("CP"));
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(LogisticsConstants.MAIL_STATUS_CREATED);
        } else {
            entity.setStatus(normalizeStatus(entity.getStatus()));
        }
        stamp(entity);
        if (mailMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to insert mail");
        }
        return entity;
    }

    @Override
    public Optional<MailEntity> findMailByWaybillNo(String waybillNo) {
        return Optional.ofNullable(mailMapper.selectOne(new LambdaQueryWrapper<MailEntity>()
                .eq(MailEntity::getWaybillNo, normalize(waybillNo))));
    }

    @Override
    public Optional<MailEntity> findMailById(Long id) {
        return Optional.ofNullable(mailMapper.selectById(id));
    }

    @Override
    public List<MailEntity> findAllMails() {
        return mailMapper.selectList(null);
    }

    @Override
    public List<MailEntity> findMailsByStatus(String status) {
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>()
                .eq(MailEntity::getStatus, normalize(status)));
    }

    @Override
    public List<MailEntity> findMailsByWaybillNos(List<String> waybillNos) {
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>()
                .in(MailEntity::getWaybillNo, waybillNos));
    }

    @Override
    public Optional<MailTypeEntity> findMailTypeByCode(String code) {
        return Optional.ofNullable(mailTypeMapper.selectOne(new LambdaQueryWrapper<MailTypeEntity>()
                .eq(MailTypeEntity::getCode, normalize(code))));
    }

    @Override
    public List<MailTypeEntity> findAllMailTypes() {
        return mailTypeMapper.selectList(new LambdaQueryWrapper<MailTypeEntity>().orderByAsc(MailTypeEntity::getId));
    }

    @Override
    public Optional<CountryEntity> findCountryByCode(String code) {
        return Optional.ofNullable(countryMapper.selectOne(new LambdaQueryWrapper<CountryEntity>()
                .eq(CountryEntity::getCode, normalize(code))));
    }

    @Override
    public List<CountryEntity> findAllCountries() {
        return countryMapper.selectList(new LambdaQueryWrapper<CountryEntity>().orderByAsc(CountryEntity::getId));
    }

    @Override
    public Optional<ServiceTypeEntity> findServiceTypeByCode(String code) {
        return Optional.ofNullable(serviceTypeMapper.selectOne(new LambdaQueryWrapper<ServiceTypeEntity>()
                .eq(ServiceTypeEntity::getCode, normalize(code))));
    }

    @Override
    public List<ServiceTypeEntity> findAllServiceTypes() {
        return serviceTypeMapper.selectList(new LambdaQueryWrapper<ServiceTypeEntity>().orderByAsc(ServiceTypeEntity::getId));
    }

    @Override
    public List<ServiceTypeEntity> findServiceTypesByCountryCode(String countryCode) {
        String normalizedCountryCode = normalize(countryCode);
        List<String> serviceCodes = countryServiceTypeMapper.selectList(new LambdaQueryWrapper<CountryServiceTypeEntity>()
                        .eq(CountryServiceTypeEntity::getCountryCode, normalizedCountryCode)
                        .eq(CountryServiceTypeEntity::getEnabled, 1))
                .stream()
                .map(CountryServiceTypeEntity::getServiceTypeCode)
                .toList();
        if (serviceCodes.isEmpty()) {
            return List.of();
        }
        return serviceTypeMapper.selectList(new LambdaQueryWrapper<ServiceTypeEntity>()
                .in(ServiceTypeEntity::getCode, serviceCodes)
                .eq(ServiceTypeEntity::getEnabled, 1)
                .orderByAsc(ServiceTypeEntity::getId));
    }

    @Override
    public Optional<SenderEntity> findSenderById(Long id) {
        return Optional.ofNullable(senderMapper.selectById(id));
    }

    @Override
    public Optional<RecipientEntity> findRecipientById(Long id) {
        return Optional.ofNullable(recipientMapper.selectById(id));
    }

    @Override
    public List<MailPackageSummaryResponse> findPackageSummaries() {
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>()
                        .isNotNull(MailEntity::getPackageId))
                .stream()
                .collect(Collectors.groupingBy(mail -> mail.getPackageId().trim().toUpperCase(), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toPackageSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(MailPackageSummaryResponse::getLatestUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Override
    public List<MailEntity> findMailsByPackageId(String packageId) {
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>()
                .eq(MailEntity::getPackageId, normalize(packageId)));
    }

    @Override
    public List<MailEntity> findPendingDeliveryMails(String currentFacilityCode) {
        if (!StringUtils.hasText(currentFacilityCode)) {
            return List.of();
        }
        String normalizedFacilityCode = currentFacilityCode.trim().toUpperCase();
        LambdaQueryWrapper<MailEntity> wrapper = new LambdaQueryWrapper<MailEntity>()
                .and(condition -> condition.isNull(MailEntity::getPackageId).or().eq(MailEntity::getPackageId, ""))
                .and(condition -> condition.isNull(MailEntity::getBagNo).or().eq(MailEntity::getBagNo, ""))
                .and(condition -> condition.isNull(MailEntity::getCurrentSlot).or().eq(MailEntity::getCurrentSlot, ""))
                .eq(MailEntity::getCurrentFacilityCode, normalizedFacilityCode)
                .and(condition -> condition
                        .eq(MailEntity::getStatus, LogisticsConstants.MAIL_STATUS_READY_FOR_DELIVERY)
                        .or()
                        .eq(MailEntity::getStatus, LogisticsConstants.MAIL_STATUS_ARRIVED))
                .orderByDesc(MailEntity::getUpdatedAt);
        return mailMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public MailEntity updateMailStatus(String waybillNo, String status, String currentFacilityCode) {
        MailEntity mail = findMailByWaybillNo(waybillNo).orElseThrow(() -> new BusinessException(404, "mail not found"));
        mail.setStatus(normalizeStatus(status));
        if (StringUtils.hasText(currentFacilityCode)) {
            mail.setCurrentFacilityCode(currentFacilityCode.trim().toUpperCase());
        }
        mail.setUpdatedAt(LocalDateTime.now());
        if (mailMapper.updateById(mail) <= 0) {
            throw new BusinessException(500, "failed to update mail status");
        }
        return mail;
    }

    @Override
    @Transactional
    public int receiveMailPackage(String packageId, String currentFacilityCode) {
        String normalizedPackageId = normalize(packageId);
        List<MailEntity> mails = findMailsByPackageId(normalizedPackageId);
        if (mails.isEmpty()) {
            throw new BusinessException(404, "package not found: " + normalizedPackageId);
        }
        int updated = 0;
        for (MailEntity mail : mails) {
            if (StringUtils.hasText(currentFacilityCode)) {
                mail.setCurrentFacilityCode(currentFacilityCode.trim().toUpperCase());
            }
            mail.setStatus(LogisticsConstants.MAIL_STATUS_ARRIVED);
            mail.setUpdatedAt(LocalDateTime.now());
            if (mailMapper.updateById(mail) > 0) {
                updated++;
            }
        }
        return updated;
    }

    @Override
    @Transactional
    public int openMailPackage(String packageId, String currentFacilityCode) {
        String normalizedPackageId = normalize(packageId);
        List<MailEntity> mails = findMailsByPackageId(normalizedPackageId);
        if (mails.isEmpty()) {
            throw new BusinessException(404, "package not found: " + normalizedPackageId);
        }
        int updated = 0;
        String normalizedFacilityCode = StringUtils.hasText(currentFacilityCode) ? currentFacilityCode.trim().toUpperCase() : null;
        for (MailEntity mail : mails) {
            LambdaUpdateWrapper<MailEntity> wrapper = new LambdaUpdateWrapper<MailEntity>()
                    .eq(MailEntity::getId, mail.getId())
                    .set(MailEntity::getPackageId, null)
                    .set(MailEntity::getBagNo, null)
                    .set(MailEntity::getCurrentSlot, null)
                    .set(MailEntity::getStatus, LogisticsConstants.MAIL_STATUS_READY_FOR_DELIVERY)
                    .set(MailEntity::getUpdatedAt, LocalDateTime.now());
            if (normalizedFacilityCode != null) {
                wrapper.set(MailEntity::getCurrentFacilityCode, normalizedFacilityCode);
            }
            if (mailMapper.update(null, wrapper) > 0) {
                updated++;
            }
        }
        return updated;
    }

    @Override
    @Transactional
    public int receiveAndOpenMailPackage(String packageId, String currentFacilityCode) {
        int received = receiveMailPackage(packageId, currentFacilityCode);
        int opened = openMailPackage(packageId, currentFacilityCode);
        return Math.max(received, opened);
    }

    @Override
    @Transactional
    public MailEntity assignMailBag(String waybillNo, String bagNo, String currentFacilityCode) {
        MailEntity mail = findMailByWaybillNo(waybillNo).orElseThrow(() -> new BusinessException(404, "mail not found"));
        if (StringUtils.hasText(bagNo)) {
            String normalizedBagNo = bagNo.trim();
            mail.setBagNo(normalizedBagNo);
            mail.setPackageId(normalizedBagNo);
            mail.setStatus(LogisticsConstants.MAIL_STATUS_DISPATCHED);
            if (StringUtils.hasText(currentFacilityCode)) {
                mail.setCurrentFacilityCode(currentFacilityCode.trim().toUpperCase());
            }
            mail.setUpdatedAt(LocalDateTime.now());
            if (mailMapper.updateById(mail) <= 0) {
                throw new BusinessException(500, "failed to assign mail bag");
            }
        } else {
            LambdaUpdateWrapper<MailEntity> wrapper = new LambdaUpdateWrapper<MailEntity>()
                    .eq(MailEntity::getWaybillNo, mail.getWaybillNo())
                    .set(MailEntity::getBagNo, null)
                    .set(MailEntity::getPackageId, null)
                    .set(MailEntity::getCurrentSlot, null)
                    .set(MailEntity::getStatus, LogisticsConstants.MAIL_STATUS_SORTED)
                    .set(MailEntity::getUpdatedAt, LocalDateTime.now());
            if (StringUtils.hasText(currentFacilityCode)) {
                wrapper.set(MailEntity::getCurrentFacilityCode, currentFacilityCode.trim().toUpperCase());
                mail.setCurrentFacilityCode(currentFacilityCode.trim().toUpperCase());
            }
            if (mailMapper.update(null, wrapper) <= 0) {
                throw new BusinessException(500, "failed to assign mail bag");
            }
            mail.setBagNo(null);
            mail.setPackageId(null);
            mail.setCurrentSlot(null);
            mail.setStatus(LogisticsConstants.MAIL_STATUS_SORTED);
        }
        return mail;
    }

    @Override
    @Transactional
    public int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode) {
        int updated = 0;
        for (String waybillNo : waybillNos) {
            assignMailBag(waybillNo, bagNo, currentFacilityCode);
            updated++;
        }
        return updated;
    }

    @Override
    @Transactional
    public MailEntity assignMailSlot(String waybillNo, String slotCode, String destinationNode, String currentFacilityCode, String status) {
        MailEntity mail = findMailByWaybillNo(waybillNo).orElseThrow(() -> new BusinessException(404, "mail not found"));
        mail.setCurrentSlot(normalizeSlot(slotCode));
        mail.setDestinationNode(StringUtils.hasText(destinationNode) ? destinationNode.trim() : null);
        if (StringUtils.hasText(currentFacilityCode)) {
            mail.setCurrentFacilityCode(currentFacilityCode.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            mail.setStatus(normalizeStatus(status));
        }
        mail.setUpdatedAt(LocalDateTime.now());
        if (mailMapper.updateById(mail) <= 0) {
            throw new BusinessException(500, "failed to assign mail slot");
        }
        return mail;
    }

    @Override
    public List<MailSlotSummaryResponse> findActiveSlots() {
        List<java.util.Map<String, Object>> rows = mailMapper.selectMaps(new QueryWrapper<MailEntity>()
                .select("current_slot", "current_facility_code", "COUNT(*) AS pending_count", "MIN(destination_node) AS destination_node")
                .isNotNull("current_slot")
                .groupBy("current_slot", "current_facility_code"));
        return rows.stream().map(row -> {
            MailSlotSummaryResponse response = new MailSlotSummaryResponse();
            String slotCode = row.get("current_slot") == null ? null : String.valueOf(row.get("current_slot"));
            String currentFacilityCode = row.get("current_facility_code") == null ? null : String.valueOf(row.get("current_facility_code"));
            String destinationNode = row.get("destination_node") == null ? null : String.valueOf(row.get("destination_node"));
            Integer pendingCount = row.get("pending_count") == null ? 0 : Integer.parseInt(String.valueOf(row.get("pending_count")));
            response.setSlotCode(slotCode == null ? null : slotCode.trim().toUpperCase());
            response.setPendingCount(pendingCount);
            response.setCurrentFacilityCode(currentFacilityCode == null ? null : currentFacilityCode.trim().toUpperCase());
            response.setDestinationNode(destinationNode);
            response.setNextHopCode(destinationNode);
            response.setBagStatus("RAW");
            response.setPreviewItemNos(List.of());
            return response;
        }).toList();
    }

    @Override
    @Transactional
    public MailSlotSealResponse sealMailSlot(String slotCode, String packageId, String destinationNode, String currentFacilityCode, String status) {
        String normalizedSlot = normalizeSlot(slotCode);
        String normalizedPackageId = StringUtils.hasText(packageId) ? packageId.trim().toUpperCase() : generatePackageId();
        String normalizedDestinationNode = StringUtils.hasText(destinationNode) ? destinationNode.trim().toUpperCase() : null;
        LambdaUpdateWrapper<MailEntity> wrapper = new LambdaUpdateWrapper<MailEntity>()
                .eq(MailEntity::getCurrentSlot, normalizedSlot)
                .set(MailEntity::getPackageId, normalizedPackageId)
                .set(MailEntity::getCurrentSlot, null)
                .set(MailEntity::getUpdatedAt, LocalDateTime.now());
        if (normalizedDestinationNode != null) {
            wrapper.set(MailEntity::getDestinationNode, normalizedDestinationNode);
        }
        if (StringUtils.hasText(currentFacilityCode)) {
            wrapper.set(MailEntity::getCurrentFacilityCode, currentFacilityCode.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            wrapper.set(MailEntity::getStatus, normalizeStatus(status));
        }

        int updated = mailMapper.update(null, wrapper);
        if (updated < 0) {
            throw new BusinessException(500, "failed to seal slot");
        }

        MailSlotSealResponse response = new MailSlotSealResponse();
        response.setSlotCode(normalizedSlot);
        response.setPackageId(normalizedPackageId);
        response.setDestinationNode(normalizedDestinationNode);
        response.setUpdatedCount(updated);
        return response;
    }

    @Override
    public boolean hasAnyData() {
        return senderMapper.selectCount(null) > 0
                || recipientMapper.selectCount(null) > 0
                || mailTypeMapper.selectCount(null) > 0
                || countryMapper.selectCount(null) > 0
                || serviceTypeMapper.selectCount(null) > 0
                || mailMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        if (hasAnyData()) {
            return;
        }

        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_PACKAGE, "PACKAGE", "package mail", 0));
        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_REGISTERED, "REGISTERED", "registered mail", 1));
        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_EMS, "EMS", "express mail", 1));

        saveServiceType(createServiceType("AIR", "AIR", "air service", 1));
        saveServiceType(createServiceType("SAL", "SAL", "surface air lifted", 1));
        saveServiceType(createServiceType("SEA", "SEA", "sea service", 1));

        saveCountry(createCountry("LN", "Liana", "Liana", 1, "OCEANIA", "default country"));
        saveCountry(createCountry("HK", "Hong Kong", "Hong Kong", 1, "ASIA_PACIFIC", "postal enabled"));
        saveCountry(createCountry("JP", "Japan", "Japan", 1, "ASIA_PACIFIC", "postal enabled"));
        saveCountry(createCountry("US", "United States", "United States", 1, "NORTH_AMERICA", "postal enabled"));
        saveCountry(createCountry("DE", "Germany", "Germany", 0, "EUROPE", "disabled example"));

        linkCountryService("LN", "AIR", 1);
        linkCountryService("LN", "SAL", 1);
        linkCountryService("LN", "SEA", 1);
        linkCountryService("HK", "AIR", 1);
        linkCountryService("HK", "SAL", 1);
        linkCountryService("HK", "SEA", 1);
        linkCountryService("JP", "AIR", 1);
        linkCountryService("JP", "SAL", 1);
        linkCountryService("JP", "SEA", 1);
        linkCountryService("US", "AIR", 1);
        linkCountryService("US", "SAL", 1);
        linkCountryService("US", "SEA", 1);
        linkCountryService("DE", "AIR", 0);
        linkCountryService("DE", "SAL", 0);
        linkCountryService("DE", "SEA", 0);

        SenderEntity sender = new SenderEntity();
        sender.setFullName("Demo Sender");
        sender.setPhone("0900123456");
        sender.setAddress("A1 Plaza, Namoa");
        sender.setPostcode("10001");
        sender.setIdType("NID");
        sender.setIdNumber("NID0001");
        saveSender(sender);

        RecipientEntity recipient = new RecipientEntity();
        recipient.setFullName("Demo Recipient");
        recipient.setPhone("0900654321");
        recipient.setAddress("B1 Central, Namoa");
        recipient.setPostcode("10002");
        saveRecipient(recipient);

        MailEntity mail = new MailEntity();
        mail.setWaybillNo(UpuBarcodeUtil.generateRandom("CP"));
        mail.setMailTypeCode(LogisticsConstants.MAIL_TYPE_REGISTERED);
        mail.setServiceType("AIR");
        mail.setMailScope("DOMESTIC");
        mail.setSenderId(sender.getId());
        mail.setRecipientId(recipient.getId());
        mail.setOriginFacilityCode("B1");
        mail.setCurrentFacilityCode("B1");
        mail.setDestFacilityCode("C1");
        mail.setWeightGrams(120);
        mail.setDeclaredValue(BigDecimal.valueOf(0));
        mail.setStatus(LogisticsConstants.MAIL_STATUS_CREATED);
        saveMail(mail);
    }

    private MailTypeEntity createMailType(String code, String name, String description, Integer requiresSignature) {
        MailTypeEntity entity = new MailTypeEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setRequiresSignature(requiresSignature);
        return entity;
    }

    private ServiceTypeEntity createServiceType(String code, String name, String description, Integer enabled) {
        ServiceTypeEntity entity = new ServiceTypeEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        return entity;
    }

    private CountryEntity createCountry(String code, String name, String englishName, Integer postalEnabled, String upuRegion, String remark) {
        CountryEntity entity = new CountryEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setEnglishName(englishName);
        entity.setPostalEnabled(postalEnabled);
        entity.setUpuRegion(upuRegion);
        entity.setRemark(remark);
        return entity;
    }

    private void linkCountryService(String countryCode, String serviceTypeCode, Integer enabled) {
        CountryServiceTypeEntity entity = new CountryServiceTypeEntity();
        entity.setCountryCode(countryCode);
        entity.setServiceTypeCode(serviceTypeCode);
        entity.setEnabled(enabled);
        saveCountryServiceType(entity);
    }

    private void stamp(SenderEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(RecipientEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(MailTypeEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(CountryEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(ServiceTypeEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(CountryServiceTypeEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(MailEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "unsupported mail status");
        }
        return normalized;
    }

    private String normalizeCountry(String countryCode) {
        return StringUtils.hasText(countryCode) ? countryCode.trim().toUpperCase() : "LN";
    }

    private String normalizeMailScope(String mailScope) {
        if (!StringUtils.hasText(mailScope)) {
            return "DOMESTIC";
        }
        String normalized = mailScope.trim().toUpperCase();
        if (!ALLOWED_MAIL_SCOPES.contains(normalized)) {
            throw new BusinessException(400, "unsupported mail scope");
        }
        return normalized;
    }

    private String normalizeServiceType(String serviceType) {
        String normalized = normalize(serviceType);
        if (!DEFAULT_SERVICE_CODES.contains(normalized)) {
            throw new BusinessException(400, "unsupported service type");
        }
        return normalized;
    }

    private String normalizeSlot(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "slotCode cannot be blank");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeDestCountryCode(String mailScope, String destCountryCode) {
        if (!"INTERNATIONAL".equals(normalizeMailScope(mailScope))) {
            return null;
        }
        String normalized = normalize(destCountryCode);
        CountryEntity country = findCountryByCode(normalized).orElseThrow(() -> new BusinessException(404, "destination country not found: " + normalized));
        if (!Integer.valueOf(1).equals(country.getPostalEnabled())) {
            throw new BusinessException(400, "destination country is not postal enabled: " + normalized);
        }
        return normalized;
    }

    private String generatePackageId() {
        return "BAG_NEW_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private MailPackageSummaryResponse toPackageSummary(String packageId, List<MailEntity> mails) {
        MailPackageSummaryResponse response = new MailPackageSummaryResponse();
        response.setPackageId(packageId);
        response.setMailCount(mails.size());
        response.setDispatchedCount((int) mails.stream().filter(mail -> LogisticsConstants.MAIL_STATUS_DISPATCHED.equals(mail.getStatus())).count());
        response.setArrivedCount((int) mails.stream().filter(mail -> LogisticsConstants.MAIL_STATUS_ARRIVED.equals(mail.getStatus())).count());
        response.setSortedCount((int) mails.stream().filter(mail -> LogisticsConstants.MAIL_STATUS_SORTED.equals(mail.getStatus())).count());
        response.setDeliveredCount((int) mails.stream().filter(mail -> LogisticsConstants.MAIL_STATUS_DELIVERED.equals(mail.getStatus())).count());
        response.setOriginFacilityCode(firstText(mails.stream().map(MailEntity::getOriginFacilityCode).toList()));
        response.setCurrentFacilityCode(firstText(mails.stream().map(MailEntity::getCurrentFacilityCode).toList()));
        response.setDestinationNode(firstText(mails.stream().map(MailEntity::getDestinationNode).toList()));
        response.setPackageStatus(resolvePackageStatus(mails));
        response.setLatestUpdatedAt(mails.stream().map(MailEntity::getUpdatedAt).filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        response.setPreviewWaybillNos(mails.stream().map(MailEntity::getWaybillNo).filter(StringUtils::hasText).limit(5).toList());
        return response;
    }

    private String resolvePackageStatus(List<MailEntity> mails) {
        if (mails == null || mails.isEmpty()) {
            return null;
        }
        if (mails.stream().anyMatch(mail -> LogisticsConstants.MAIL_STATUS_DELIVERED.equals(mail.getStatus()))) {
            return LogisticsConstants.MAIL_STATUS_DELIVERED;
        }
        if (mails.stream().anyMatch(mail -> LogisticsConstants.MAIL_STATUS_ARRIVED.equals(mail.getStatus()))) {
            return LogisticsConstants.MAIL_STATUS_ARRIVED;
        }
        if (mails.stream().anyMatch(mail -> LogisticsConstants.MAIL_STATUS_DISPATCHED.equals(mail.getStatus()))) {
            return LogisticsConstants.MAIL_STATUS_DISPATCHED;
        }
        return mails.get(0).getStatus();
    }

    private String firstText(List<String> values) {
        return values.stream().filter(StringUtils::hasText).findFirst().orElse(null);
    }
}
