package com.liana.post.facility.client;

import com.liana.post.common.model.Result;
import com.liana.post.facility.model.dto.CountryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "liana-oms-service", contextId = "facilityOmsClient", url = "${oms.service.url:}")
public interface OmsClient {

    @GetMapping("/api/oms/countries")
    Result<List<CountryResponse>> listCountries();
}
