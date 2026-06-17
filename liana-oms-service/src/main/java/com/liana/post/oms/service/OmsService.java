package com.liana.post.oms.service;

import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
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

import java.util.List;

public interface OmsService {
    MailResponse createMail(MailCreateRequest request);
    MailResponse getMailByBarcode(String barcode);
    MailResponse getMailByWaybillNo(String waybillNo);
    List<MailResponse> listMails();
    List<MailResponse> listMailsByStatus(String status);
    List<CountryResponse> listCountries();
    List<ServiceTypeResponse> listServiceTypes();
    List<ServiceTypeResponse> listServiceTypesByCountry(String countryCode);
    List<MailDispatchCandidateResponse> listDispatchCandidates(MailDispatchCandidateQueryRequest request);
    List<MailPackageSummaryResponse> listPackages();
    List<MailResponse> listPendingDeliveryMails(String currentFacilityCode);
    MailResponse updateMailStatus(String waybillNo, MailStatusUpdateRequest request);
    MailResponse assignMailBag(String waybillNo, MailBagAssignRequest request);
    int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode);
    MailResponse updateMailRoute(String waybillNo, MailRouteAssignRequest request);
    int receiveAndOpenMailPackage(String packageId, String currentFacilityCode);
    List<MailSlotSummaryResponse> listActiveSlots();
    MailSlotSealResponse sealMailSlot(String slotCode, MailSlotSealRequest request);
    MailResponse deliverMail(String waybillNo, String facilityCode);
    MailResponse departExchangeMail(String waybillNo, String facilityCode);
    void initDefaults();
}
