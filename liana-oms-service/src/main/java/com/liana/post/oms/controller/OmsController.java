package com.liana.post.oms.controller;

import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.model.Result;
import com.liana.post.oms.model.dto.CountryResponse;
import com.liana.post.oms.model.dto.MailBagAssignRequest;
import com.liana.post.oms.model.dto.MailCreateRequest;
import com.liana.post.oms.model.dto.MailResponse;
import com.liana.post.oms.model.dto.MailStatusUpdateRequest;
import com.liana.post.oms.model.dto.MailTypeResponse;
import com.liana.post.oms.model.dto.ServiceTypeResponse;
import com.liana.post.oms.repository.OmsRepository;
import com.liana.post.oms.service.OmsService;
import com.liana.post.oms.util.OmsMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/oms")
public class OmsController {

    private final OmsService omsService;
    private final OmsRepository omsRepository;

    public OmsController(OmsService omsService, OmsRepository omsRepository) {
        this.omsService = omsService;
        this.omsRepository = omsRepository;
    }

    @PostConstruct
    public void init() {
        omsService.initDefaults();
    }

    @PostMapping("/mails")
    public Result<MailResponse> createMail(@Valid @RequestBody MailCreateRequest request) {
        return Result.ok(omsService.createMail(request));
    }

    @GetMapping("/mails/{waybillNo}")
    public Result<MailResponse> getMail(@PathVariable("waybillNo") String waybillNo) {
        return Result.ok(omsService.getMailByWaybillNo(waybillNo));
    }

    @GetMapping("/mails/by-barcode")
    public Result<MailResponse> getMailByBarcode(@RequestParam String barcode) {
        return Result.ok(omsService.getMailByBarcode(barcode));
    }

    @GetMapping("/mails")
    public Result<List<MailResponse>> listMails() {
        return Result.ok(omsService.listMails());
    }

    @GetMapping("/mails/status/{status}")
    public Result<List<MailResponse>> listMailsByStatus(@PathVariable("status") String status) {
        return Result.ok(omsService.listMailsByStatus(status));
    }

    @PostMapping("/mails/dispatch-candidates")
    public Result<List<MailDispatchCandidateResponse>> listDispatchCandidates(@RequestBody MailDispatchCandidateQueryRequest request) {
        return Result.ok(omsService.listDispatchCandidates(request));
    }

    @PostMapping("/mails/{waybillNo}/status")
    public Result<MailResponse> updateMailStatus(@PathVariable("waybillNo") String waybillNo, @Valid @RequestBody MailStatusUpdateRequest request) {
        return Result.ok(omsService.updateMailStatus(waybillNo, request));
    }

    @PostMapping("/mails/status/batch")
    public Result<Integer> updateMailStatuses(@Valid @RequestBody MailBagSyncRequest request) {
        return Result.ok(omsService.assignMailBagToWaybillNos(request.getWaybillNos(), request.getBagNo(), request.getCurrentFacilityCode()));
    }

    @PostMapping("/mails/{waybillNo}/bag")
    public Result<MailResponse> assignMailBag(@PathVariable("waybillNo") String waybillNo, @Valid @RequestBody MailBagAssignRequest request) {
        return Result.ok(omsService.assignMailBag(waybillNo, request));
    }

    @GetMapping({"/mail-types", "/types", "/mail-types/list"})
    public Result<List<MailTypeResponse>> listMailTypes() {
        return Result.ok(omsRepository.findAllMailTypes().stream().map(OmsMapper::toMailTypeResponse).toList());
    }

    @GetMapping({"/countries", "/country/list"})
    public Result<List<CountryResponse>> listCountries() {
        return Result.ok(omsService.listCountries());
    }

    @GetMapping({"/service-types", "/service-types/list"})
    public Result<List<ServiceTypeResponse>> listServiceTypes() {
        return Result.ok(omsService.listServiceTypes());
    }

    @GetMapping("/service-types/by-country")
    public Result<List<ServiceTypeResponse>> listServiceTypesByCountry(@RequestParam("countryCode") String countryCode) {
        return Result.ok(omsService.listServiceTypesByCountry(countryCode));
    }
}
