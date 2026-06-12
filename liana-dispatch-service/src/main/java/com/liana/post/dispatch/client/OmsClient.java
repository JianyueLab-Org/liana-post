package com.liana.post.dispatch.client;

import com.liana.post.common.dto.dispatch.MailDispatchCandidateQueryRequest;
import com.liana.post.common.dto.dispatch.MailDispatchCandidateResponse;
import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.model.Result;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-oms-service", contextId = "dispatchOmsClient")
public interface OmsClient {

    @PostMapping("/api/oms/mails/dispatch-candidates")
    Result<List<MailDispatchCandidateResponse>> listDispatchCandidates(@RequestBody MailDispatchCandidateQueryRequest request);

    @PostMapping("/api/oms/mails/status/batch")
    Result<?> updateMailStatuses(@RequestBody MailBagSyncRequest request);
}