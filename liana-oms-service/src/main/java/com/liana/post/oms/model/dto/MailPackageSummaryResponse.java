package com.liana.post.oms.model.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MailPackageSummaryResponse {
    private String packageId;
    private String originFacilityCode;
    private String currentFacilityCode;
    private String destinationNode;
    private String packageStatus;
    private Integer mailCount;
    private Integer dispatchedCount;
    private Integer arrivedCount;
    private Integer sortedCount;
    private Integer deliveredCount;
    private LocalDateTime latestUpdatedAt;
    private List<String> previewWaybillNos = new ArrayList<>();

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getOriginFacilityCode() {
        return originFacilityCode;
    }

    public void setOriginFacilityCode(String originFacilityCode) {
        this.originFacilityCode = originFacilityCode;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }

    public String getDestinationNode() {
        return destinationNode;
    }

    public void setDestinationNode(String destinationNode) {
        this.destinationNode = destinationNode;
    }

    public String getPackageStatus() {
        return packageStatus;
    }

    public void setPackageStatus(String packageStatus) {
        this.packageStatus = packageStatus;
    }

    public Integer getMailCount() {
        return mailCount;
    }

    public void setMailCount(Integer mailCount) {
        this.mailCount = mailCount;
    }

    public Integer getDispatchedCount() {
        return dispatchedCount;
    }

    public void setDispatchedCount(Integer dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }

    public Integer getArrivedCount() {
        return arrivedCount;
    }

    public void setArrivedCount(Integer arrivedCount) {
        this.arrivedCount = arrivedCount;
    }

    public Integer getSortedCount() {
        return sortedCount;
    }

    public void setSortedCount(Integer sortedCount) {
        this.sortedCount = sortedCount;
    }

    public Integer getDeliveredCount() {
        return deliveredCount;
    }

    public void setDeliveredCount(Integer deliveredCount) {
        this.deliveredCount = deliveredCount;
    }

    public LocalDateTime getLatestUpdatedAt() {
        return latestUpdatedAt;
    }

    public void setLatestUpdatedAt(LocalDateTime latestUpdatedAt) {
        this.latestUpdatedAt = latestUpdatedAt;
    }

    public List<String> getPreviewWaybillNos() {
        return previewWaybillNos;
    }

    public void setPreviewWaybillNos(List<String> previewWaybillNos) {
        this.previewWaybillNos = previewWaybillNos;
    }
}
