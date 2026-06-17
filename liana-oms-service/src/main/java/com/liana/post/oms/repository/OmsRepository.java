package com.liana.post.oms.repository;

import com.liana.post.oms.model.entity.MailEntity;
import com.liana.post.oms.model.entity.CountryEntity;
import com.liana.post.oms.model.entity.CountryServiceTypeEntity;
import com.liana.post.oms.model.entity.MailTypeEntity;
import com.liana.post.oms.model.entity.ServiceTypeEntity;
import com.liana.post.oms.model.entity.RecipientEntity;
import com.liana.post.oms.model.entity.SenderEntity;
import com.liana.post.oms.model.dto.MailPackageSummaryResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import com.liana.post.oms.model.dto.MailSlotSealResponse;

import java.util.List;
import java.util.Optional;

public interface OmsRepository {
    SenderEntity saveSender(SenderEntity entity);
    RecipientEntity saveRecipient(RecipientEntity entity);
    MailTypeEntity saveMailType(MailTypeEntity entity);
    CountryEntity saveCountry(CountryEntity entity);
    ServiceTypeEntity saveServiceType(ServiceTypeEntity entity);
    CountryServiceTypeEntity saveCountryServiceType(CountryServiceTypeEntity entity);
    MailEntity saveMail(MailEntity entity);
    Optional<MailEntity> findMailByWaybillNo(String waybillNo);
    Optional<MailEntity> findMailById(Long id);
    List<MailEntity> findAllMails();
    List<MailEntity> findMailsByStatus(String status);
    List<MailEntity> findMailsByWaybillNos(List<String> waybillNos);
    Optional<MailTypeEntity> findMailTypeByCode(String code);
    List<MailTypeEntity> findAllMailTypes();
    Optional<CountryEntity> findCountryByCode(String code);
    List<CountryEntity> findAllCountries();
    Optional<ServiceTypeEntity> findServiceTypeByCode(String code);
    List<ServiceTypeEntity> findAllServiceTypes();
    List<ServiceTypeEntity> findServiceTypesByCountryCode(String countryCode);
    Optional<SenderEntity> findSenderById(Long id);
    Optional<RecipientEntity> findRecipientById(Long id);
    List<MailPackageSummaryResponse> findPackageSummaries();
    List<MailEntity> findMailsByPackageId(String packageId);
    List<MailEntity> findPendingDeliveryMails(String currentFacilityCode);
    MailEntity updateMailStatus(String waybillNo, String status, String currentFacilityCode);
    MailEntity assignMailBag(String waybillNo, String bagNo, String currentFacilityCode);
    int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode);
    MailEntity assignMailSlot(String waybillNo, String slotCode, String destinationNode, String currentFacilityCode, String status);
    int receiveMailPackage(String packageId, String currentFacilityCode);
    int openMailPackage(String packageId, String currentFacilityCode);
    int receiveAndOpenMailPackage(String packageId, String currentFacilityCode);
    List<MailSlotSummaryResponse> findActiveSlots();
    MailSlotSealResponse sealMailSlot(String slotCode, String packageId, String destinationNode, String currentFacilityCode, String status);
    boolean hasAnyData();
    default void seedDefaults() {
    }
}
