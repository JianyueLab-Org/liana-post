package com.liana.post.sorting.service;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.sorting.model.dto.*;

import java.util.List;

public interface SortingService {
    void syncManifest(ManifestDTO request);
    SortingPackageResponse receive(SortingReceiveRequest request);
    List<SortingLineResponse> unpackItem(SortingUnpackItemRequest request);
    SortingSecurityAuditResponse routeCalculate(SortingRouteScanRequest request);
    SortingRouteResponse routeCalculate(SortingRouteCalculateRequest request);
    List<SortingPendingRouteItemResponse> listPendingRouteItems(String stationCode);
    SortingPackageResponse reBag(SortingRebagRequest request);
    List<SortingSlotSummaryResponse> listSlotSummaries(String stationCode);
    SortingPackageResponse sealBagBySlot(String slotCode, String stationCode, String operatorId);
    List<CountrySlotSummaryResponse> listCountrySlotSummaries(String stationCode);
    List<SortingPackageResponse> sealCountrySlot(CountrySlotBulkRequest request);
    List<SortingPackageResponse> listPackages(String authorization);
    List<SortingManifestResponse> listManifests(boolean receiveCandidate, String authorization);
    SortingManifestDetailResponse getManifestDetail(String manifestNo);
    List<SortingLineResponse> listLines(String packageNo);
    List<SortingDiscrepancyResponse> listDiscrepancies(String packageNo);
    DashboardSummaryResponse dashboardSummary(String stationCode);
    void initDefaults();
}
