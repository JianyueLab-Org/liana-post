
package com.liana.post.tracking.client;

import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "liana-oms-service", contextId = "trackingOmsClient", url = "${oms.service.url:}")
public interface OMSClient {

    @PostMapping("/api/oms/mails/dispatch-candidates")
    Result<List<MailDispatchCandidateResponse>> listDispatchCandidates(@RequestBody MailDispatchCandidateQueryRequest request);
}
