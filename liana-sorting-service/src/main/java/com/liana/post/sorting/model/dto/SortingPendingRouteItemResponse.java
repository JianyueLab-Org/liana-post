package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingPendingRouteItemResponse {
    private String itemNo;
    private String sourcePackageNo;
    private String manifestNo;
    private String stationCode;
    private LocalDateTime unpackedAt;
    private String mailScope;
    private String serviceType;
    private String originFacilityCode;
    private String currentFacilityCode;
    private String status;

    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getSourcePackageNo() { return sourcePackageNo; }
    public void setSourcePackageNo(String sourcePackageNo) { this.sourcePackageNo = sourcePackageNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public LocalDateTime getUnpackedAt() { return unpackedAt; }
    public void setUnpackedAt(LocalDateTime unpackedAt) { this.unpackedAt = unpackedAt; }
    public String getMailScope() { return mailScope; }
    public void setMailScope(String mailScope) { this.mailScope = mailScope; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getCurrentFacilityCode() { return currentFacilityCode; }
    public void setCurrentFacilityCode(String currentFacilityCode) { this.currentFacilityCode = currentFacilityCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
