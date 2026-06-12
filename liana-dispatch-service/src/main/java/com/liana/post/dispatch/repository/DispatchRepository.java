package com.liana.post.dispatch.repository;

import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.model.entity.HandoffRecordEntity;
import com.liana.post.dispatch.model.entity.RouteRuleEntity;

import java.util.List;
import java.util.Optional;

public interface DispatchRepository {
    RouteRuleEntity saveRouteRule(RouteRuleEntity entity);
    DispatchBagEntity saveDispatchBag(DispatchBagEntity entity);
    DispatchBagEntity updateDispatchBag(DispatchBagEntity entity);
    DispatchBatchEntity saveDispatchBatch(DispatchBatchEntity entity);
    DispatchBatchEntity updateDispatchBatch(DispatchBatchEntity entity);
    HandoffRecordEntity saveHandoffRecord(HandoffRecordEntity entity);
    Optional<RouteRuleEntity> findRouteRuleByCode(String ruleCode);
    Optional<RouteRuleEntity> findBestRouteRule(String sourceFacilityCode, String targetFacilityCode);
    Optional<DispatchBagEntity> findDispatchBagById(Long id);
    Optional<DispatchBagEntity> findDispatchBagByBagNo(String bagNo);
    DispatchBagEntity linkTransportTask(Long id, String transportTaskCode);
    Optional<DispatchBatchEntity> findDispatchBatchByBatchNo(String batchNo);
    Optional<DispatchBatchEntity> findDispatchBatchByBagNo(String bagNo);
    Optional<HandoffRecordEntity> findHandoffRecordByHandoffNo(String handoffNo);
    List<DispatchBagEntity> findAllDispatchBags();
    List<DispatchBatchEntity> findAllDispatchBatches();
    List<HandoffRecordEntity> findAllHandoffRecords();
    int assignMailBagToWaybillNos(List<String> waybillNos, String bagNo, String currentFacilityCode);
    boolean hasAnyData();
    default void seedDefaults() {
    }
}
