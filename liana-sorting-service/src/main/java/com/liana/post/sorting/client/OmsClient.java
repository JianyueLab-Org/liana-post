package com.liana.post.sorting.client;

import com.liana.post.common.model.Result;
import com.liana.post.oms.model.dto.MailBagAssignRequest;
import com.liana.post.oms.model.dto.MailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "liana-oms-service", contextId = "sortingOmsClient", url = "${oms.service.url:}")
public interface OmsClient {
    @GetMapping("/api/oms/mails/{waybillNo}")
    Result<MailResponse> getMail(@PathVariable("waybillNo") String waybillNo);

    @PostMapping("/api/oms/mails/{waybillNo}/bag")
    Result<MailResponse> updateMailBag(@PathVariable("waybillNo") String waybillNo, @RequestBody MailBagAssignRequest request);
}
