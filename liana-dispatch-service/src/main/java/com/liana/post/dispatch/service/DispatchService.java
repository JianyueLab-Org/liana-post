package com.liana.post.dispatch.service;

import com.liana.post.dispatch.model.dto.DispatchBagCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBagResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchApproveRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchCreateRequest;
import com.liana.post.dispatch.model.dto.DispatchBatchResponse;
import com.liana.post.dispatch.model.dto.DispatchMailSyncRequest;
import com.liana.post.dispatch.model.dto.HandoffCreateRequest;
import com.liana.post.dispatch.model.dto.HandoffRecordResponse;
import com.liana.post.dispatch.model.dto.RouteRuleCreateRequest;
import com.liana.post.dispatch.model.entity.RouteRuleEntity;

import java.util.List;

public interface DispatchService {
    RouteRuleEntity createRouteRule(RouteRuleCreateRequest request);
    DispatchBagResponse createDispatchBag(DispatchBagCreateRequest request);
    DispatchBatchResponse createDispatchBatch(DispatchBatchCreateRequest request);
    DispatchBatchResponse approveDispatchBatch(String batchNo, DispatchBatchApproveRequest request);
    HandoffRecordResponse createHandoffRecord(HandoffCreateRequest request);
    DispatchBagResponse syncMailBag(DispatchMailSyncRequest request);
    DispatchBagResponse getDispatchBagById(Long id);
    DispatchBagResponse linkTransportTask(Long id, DispatchTransportTaskLinkRequest request);
    List<DispatchBagResponse> listDispatchBags();
    List<DispatchBatchResponse> listDispatchBatches();
    List<HandoffRecordResponse> listHandoffRecords();
    void initDefaults();
}
