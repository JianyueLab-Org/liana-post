package com.liana.post.sorting.client;

import com.liana.post.common.model.Result;
import com.liana.post.oms.model.dto.CountryResponse;
import com.liana.post.oms.model.dto.MailRouteAssignRequest;
import com.liana.post.oms.model.dto.MailBagAssignRequest;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.oms.model.dto.MailSlotSealRequest;
import com.liana.post.oms.model.dto.MailSlotSealResponse;
import com.liana.post.oms.model.dto.MailSlotSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "liana-oms-service", contextId = "sortingOmsClient", url = "${oms.service.url:}")
public interface OmsClient {
    @GetMapping("/api/oms/mails/{waybillNo}")
    Result<MailResponse> getMail(@PathVariable("waybillNo") String waybillNo);

    @GetMapping("/api/oms/mails")
    Result<java.util.List<MailResponse>> listMails();

    @GetMapping("/api/oms/countries")
    Result<java.util.List<CountryResponse>> listCountries();

    @PostMapping("/api/oms/mails/{waybillNo}/bag")
    Result<MailResponse> updateMailBag(@PathVariable("waybillNo") String waybillNo, @RequestBody MailBagAssignRequest request);

    @PostMapping("/api/oms/mails/{waybillNo}/route")
    Result<MailResponse> updateMailRoute(@PathVariable("waybillNo") String waybillNo, @RequestBody MailRouteAssignRequest request);

    @GetMapping("/api/oms/slots")
    Result<java.util.List<MailSlotSummaryResponse>> listActiveSlots();

    @PostMapping("/api/oms/slots/{slotCode}/seal")
    Result<MailSlotSealResponse> sealMailSlot(@PathVariable("slotCode") String slotCode, @RequestBody MailSlotSealRequest request);

    @PostMapping("/api/oms/slots/{slotCode}/rebag")
    Result<MailSlotSealResponse> rebagMailSlot(@PathVariable("slotCode") String slotCode, @RequestBody MailSlotSealRequest request);

    @PostMapping("/api/oms/mails/{waybillNo}/deliver")
    Result<MailResponse> deliverMail(@PathVariable("waybillNo") String waybillNo, @RequestParam(name = "facilityCode", required = false) String facilityCode);

    @PostMapping("/api/oms/mails/{waybillNo}/exchange-depart")
    Result<MailResponse> departExchangeMail(@PathVariable("waybillNo") String waybillNo, @RequestParam(name = "facilityCode", required = false) String facilityCode);
}
