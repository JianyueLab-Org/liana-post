
package com.liana.post.tracking.client;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.MailBagSyncRequest;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-dispatch-service", contextId = "trackingDispatchClient", url = "${dispatch.service.url:}")
public interface DispatchClient {

    @PostMapping("/api/dispatch/bags/sync-mail")
    Result<DispatchBagBriefResponse> syncMailBag(@RequestBody MailBagSyncRequest request);
}
