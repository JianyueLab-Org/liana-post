package com.liana.post.sorting.model.dto;

import java.util.ArrayList;
import java.util.List;

public class CountrySlotSummaryResponse {
    private String countryCode;
    private String countryName;
    private Integer pendingCount;
    private String previewItemNo;
    private List<String> previewItemNos = new ArrayList<>();
    private String bagStatus;
    private String exportFacilityCode;
    private String routeCode;

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }
    public String getPreviewItemNo() { return previewItemNo; }
    public void setPreviewItemNo(String previewItemNo) { this.previewItemNo = previewItemNo; }
    public List<String> getPreviewItemNos() { return previewItemNos; }
    public void setPreviewItemNos(List<String> previewItemNos) { this.previewItemNos = previewItemNos; }
    public String getBagStatus() { return bagStatus; }
    public void setBagStatus(String bagStatus) { this.bagStatus = bagStatus; }
    public String getExportFacilityCode() { return exportFacilityCode; }
    public void setExportFacilityCode(String exportFacilityCode) { this.exportFacilityCode = exportFacilityCode; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
}
