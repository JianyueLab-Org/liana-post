package com.liana.post.sorting.model.dto;

import java.util.ArrayList;
import java.util.List;

public class SortingSlotSummaryResponse {
    private String slotCode;
    private String destinationOrgCode;
    private Integer pendingCount;
    private String previewItemNo;
    private List<String> previewItemNos = new ArrayList<>();
    private String routeCode;
    private String bagStatus;

    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String destinationOrgCode) { this.destinationOrgCode = destinationOrgCode; }
    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }
    public String getPreviewItemNo() { return previewItemNo; }
    public void setPreviewItemNo(String previewItemNo) { this.previewItemNo = previewItemNo; }
    public List<String> getPreviewItemNos() { return previewItemNos; }
    public void setPreviewItemNos(List<String> previewItemNos) { this.previewItemNos = previewItemNos; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getBagStatus() { return bagStatus; }
    public void setBagStatus(String bagStatus) { this.bagStatus = bagStatus; }
}
