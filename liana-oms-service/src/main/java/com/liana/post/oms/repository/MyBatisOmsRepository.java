package com.liana.post.oms.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.common.constant.LogisticsConstants;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.mapper.CountryMapper;
import com.liana.post.oms.mapper.CountryServiceTypeMapper;
import com.liana.post.oms.mapper.MailMapper;
import com.liana.post.oms.mapper.MailTypeMapper;
import com.liana.post.oms.mapper.RecipientMapper;
import com.liana.post.oms.mapper.SenderMapper;
import com.liana.post.oms.mapper.ServiceTypeMapper;
import com.liana.post.oms.model.entity.CountryEntity;
import com.liana.post.oms.model.entity.CountryServiceTypeEntity;
import com.liana.post.oms.model.entity.MailEntity;
import com.liana.post.oms.model.entity.MailTypeEntity;
import com.liana.post.oms.model.entity.RecipientEntity;
import com.liana.post.oms.model.entity.SenderEntity;
import com.liana.post.oms.model.entity.ServiceTypeEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MyBatisOmsRepository implements OmsRepository {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            LogisticsConstants.MAIL_STATUS_CREATED,
            LogisticsConstants.MAIL_STATUS_ACCEPTED,
            LogisticsConstants.MAIL_STATUS_SORTED,
            LogisticsConstants.MAIL_STATUS_DISPATCHED,
            LogisticsConstants.MAIL_STATUS_ARRIVED,
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
        if (senderMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert sender");
        return entity;
    }

    @Override
    @Transactional
    public RecipientEntity saveRecipient(RecipientEntity entity) {
        entity.setCountryCode(normalizeCountry(entity.getCountryCode()));
        stamp(entity);
        if (recipientMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert recipient");
        return entity;
    }

    @Override
    @Transactional
    public MailTypeEntity saveMailType(MailTypeEntity entity) {
        stamp(entity);
        if (mailTypeMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert mail type");
        return entity;
    }

    @Override
    @Transactional
    public CountryEntity saveCountry(CountryEntity entity) {
        stamp(entity);
        if (countryMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert country");
        return entity;
    }

    @Override
    @Transactional
    public ServiceTypeEntity saveServiceType(ServiceTypeEntity entity) {
        stamp(entity);
        if (serviceTypeMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert service type");
        return entity;
    }

    @Override
    @Transactional
    public CountryServiceTypeEntity saveCountryServiceType(CountryServiceTypeEntity entity) {
        stamp(entity);
        if (countryServiceTypeMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert country service type");
        return entity;
    }

    @Override
    @Transactional
    public MailEntity saveMail(MailEntity entity) {
        entity.setMailScope(normalizeMailScope(entity.getMailScope()));
        entity.setServiceType(normalizeServiceType(entity.getServiceType()));
        entity.setDestCountryCode(normalizeDestCountryCode(entity.getMailScope(), entity.getDestCountryCode()));
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(LogisticsConstants.MAIL_STATUS_CREATED);
        }
        stamp(entity);
        if (mailMapper.insert(entity) <= 0) throw new BusinessException(500, "failed to insert mail");
        return entity;
    }

    @Override
    public Optional<MailEntity> findMailByWaybillNo(String waybillNo) {
        return Optional.ofNullable(mailMapper.selectOne(new LambdaQueryWrapper<MailEntity>().eq(MailEntity::getWaybillNo, normalize(waybillNo))));
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
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>().eq(MailEntity::getStatus, normalize(status)));
    }

    @Override
    public List<MailEntity> findMailsByWaybillNos(List<String> waybillNos) {
        return mailMapper.selectList(new LambdaQueryWrapper<MailEntity>().in(MailEntity::getWaybillNo, waybillNos));
    }

    @Override
    public Optional<MailTypeEntity> findMailTypeByCode(String code) {
        return Optional.ofNullable(mailTypeMapper.selectOne(new LambdaQueryWrapper<MailTypeEntity>().eq(MailTypeEntity::getCode, normalize(code))));
    }

    @Override
    public List<MailTypeEntity> findAllMailTypes() {
        return mailTypeMapper.selectList(new LambdaQueryWrapper<MailTypeEntity>().orderByAsc(MailTypeEntity::getId));
    }

    @Override
    public Optional<CountryEntity> findCountryByCode(String code) {
        return Optional.ofNullable(countryMapper.selectOne(new LambdaQueryWrapper<CountryEntity>().eq(CountryEntity::getCode, normalize(code))));
    }

    @Override
    public List<CountryEntity> findAllCountries() {
        return countryMapper.selectList(new LambdaQueryWrapper<CountryEntity>().orderByAsc(CountryEntity::getId));
    }

    @Override
    public Optional<ServiceTypeEntity> findServiceTypeByCode(String code) {
        return Optional.ofNullable(serviceTypeMapper.selectOne(new LambdaQueryWrapper<ServiceTypeEntity>().eq(ServiceTypeEntity::getCode, normalize(code))));
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
    @Transactional
    public MailEntity updateMailStatus(String waybillNo, String status, String currentFacilityCode) {
        MailEntity mail = findMailByWaybillNo(waybillNo).orElseThrow(() -> new BusinessException(404, "mail not found"));
        mail.setStatus(normalizeStatus(status));
        if (currentFacilityCode != null && !currentFacilityCode.isBlank()) {
            mail.setCurrentFacilityCode(currentFacilityCode.trim());
        }
        mail.setUpdatedAt(LocalDateTime.now());
        if (mailMapper.updateById(mail) <= 0) throw new BusinessException(500, "failed to update mail status");
        return mail;
    }

    @Override
    @Transactional
    public MailEntity assignMailBag(String waybillNo, String bagNo, String currentFacilityCode) {
        MailEntity mail = findMailByWaybillNo(waybillNo).orElseThrow(() -> new BusinessException(404, "mail not found"));
        if (bagNo == null || bagNo.isBlank()) throw new BusinessException(400, "bagNo cannot be blank");
        mail.setBagNo(bagNo.trim());
        mail.setStatus(LogisticsConstants.MAIL_STATUS_DISPATCHED);
        if (currentFacilityCode != null && !currentFacilityCode.isBlank()) {
            mail.setCurrentFacilityCode(currentFacilityCode.trim());
        }
        mail.setUpdatedAt(LocalDateTime.now());
        if (mailMapper.updateById(mail) <= 0) throw new BusinessException(500, "failed to assign mail bag");
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
        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_PACKAGE, "包裹", "普通包裹", 0));
        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_REGISTERED, "挂号信", "挂号信函件", 1));
        saveMailType(createMailType(LogisticsConstants.MAIL_TYPE_EMS, "EMS", "特快专递", 1));

        saveServiceType(createServiceType("AIR", "航空", "航空运输服务", 1));
        saveServiceType(createServiceType("SAL", "SAL", "空陆联运服务", 1));
        saveServiceType(createServiceType("SEA", "海运", "海运运输服务", 1));

        saveCountry(createCountry("LN", "利亚纳", "Liana", 1, "OCEANIA", "默认本国示例"));
        saveCountry(createCountry("HK", "中国香港", "Hong Kong", 1, "ASIA_PACIFIC", "UPU示例目的国"));
        saveCountry(createCountry("JP", "日本", "Japan", 1, "ASIA_PACIFIC", "UPU示例目的国"));
        saveCountry(createCountry("US", "美国", "United States", 1, "NORTH_AMERICA", "UPU示例目的国"));
        saveCountry(createCountry("DE", "德国", "Germany", 0, "EUROPE", "示例：当前未通邮"));

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
        mail.setWaybillNo(com.liana.post.common.util.UpuBarcodeUtil.generateRandom("CP"));
        mail.setMailTypeCode(LogisticsConstants.MAIL_TYPE_REGISTERED);
        mail.setServiceType("AIR");
        mail.setMailScope("DOMESTIC");
        mail.setDestCountryCode(null);
        mail.setSenderId(sender.getId());
        mail.setRecipientId(recipient.getId());
        mail.setOriginFacilityCode("B1");
        mail.setCurrentFacilityCode("B1");
        mail.setDestFacilityCode("C1");
        mail.setWeightGrams(120);
        mail.setDeclaredValue(null);
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

    private void stamp(SenderEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(RecipientEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(MailTypeEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(CountryEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(ServiceTypeEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(CountryServiceTypeEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }
    private void stamp(MailEntity entity) { entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now()); }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new BusinessException(400, "code cannot be blank");
        return value.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (!ALLOWED_STATUSES.contains(normalized)) throw new BusinessException(400, "unsupported mail status");
        return normalized;
    }

    private String normalizeCountry(String countryCode) {
        return countryCode == null || countryCode.isBlank() ? "LN" : countryCode.trim().toUpperCase();
    }

    private String normalizeMailScope(String mailScope) {
        if (mailScope == null || mailScope.isBlank()) {
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

    private String normalizeDestCountryCode(String mailScope, String destCountryCode) {
        if (!"INTERNATIONAL".equals(normalizeMailScope(mailScope))) {
            return null;
        }
        String normalized = normalize(destCountryCode);
        if (findCountryByCode(normalized).isEmpty()) {
            throw new BusinessException(404, "destination country not found: " + normalized);
        }
        if (findCountryByCode(normalized).map(CountryEntity::getPostalEnabled).orElse(0) != 1) {
            throw new BusinessException(400, "destination country is not postal enabled: " + normalized);
        }
        return normalized;
    }
}
