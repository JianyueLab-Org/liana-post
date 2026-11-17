package com.liana.post.dispatch.model.dto;

import java.util.ArrayList;
import java.util.List;

public class DispatchBatchResponse {
    private Long id;
    private String batchNo;
    private String manifestNo;
    private String sourceOrgCode;
    private String destinationOrgCode;
    private String facilityRouteCode;
    private String manifestSyncStatus;
    private String manifestSealStatus;
    private String manifestSealError;
    private String manifestCancelStatus;
    private String manifestCancelError;
    private String manifestCancelReason;
    private String bagNo;
    private List<String> bagNos = new ArrayList<>();
    private String routeCode;
    private String status;
    private Long approvedBy;
    private String approvedAt;

    public Long getId() { return id; }
    public String getSourceOrgCode() { return sourceOrgCode; }
    public void setSourceOrgCode(String value) { sourceOrgCode = value; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String value) { destinationOrgCode = value; }
    public String getFacilityRouteCode() { return facilityRouteCode; }
    public void setFacilityRouteCode(String value) { facilityRouteCode = value; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getManifestSyncStatus() { return manifestSyncStatus; }
    public void setManifestSyncStatus(String manifestSyncStatus) { this.manifestSyncStatus = manifestSyncStatus; }
    public String getManifestSealStatus() { return manifestSealStatus; }
    public void setManifestSealStatus(String manifestSealStatus) { this.manifestSealStatus = manifestSealStatus; }
    public String getManifestSealError() { return manifestSealError; }
    public void setManifestSealError(String manifestSealError) { this.manifestSealError = manifestSealError; }
    public String getManifestCancelStatus() { return manifestCancelStatus; }
    public void setManifestCancelStatus(String value) { manifestCancelStatus = value; }
    public String getManifestCancelError() { return manifestCancelError; }
    public void setManifestCancelError(String value) { manifestCancelError = value; }
    public String getManifestCancelReason() { return manifestCancelReason; }
    public void setManifestCancelReason(String value) { manifestCancelReason = value; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public List<String> getBagNos() { return bagNos; }
    public void setBagNos(List<String> bagNos) { this.bagNos = bagNos; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public String getApprovedAt() { return approvedAt; }
    public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
}
