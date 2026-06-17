package com.liana.post.oms.model.dto;

import java.util.ArrayList;
import java.util.List;

public class MailSlotSummaryResponse {
    private String slotCode;
    private Integer pendingCount;
    private String currentFacilityCode;
    private String destinationNode;
    private String nextHopCode;
    private String bagStatus;
    private List<String> previewItemNos = new ArrayList<>();

    public String getSlotCode() {
        return slotCode;
    }

    public void setSlotCode(String slotCode) {
        this.slotCode = slotCode;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount) {
        this.pendingCount = pendingCount;
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

    public String getNextHopCode() {
        return nextHopCode;
    }

    public void setNextHopCode(String nextHopCode) {
        this.nextHopCode = nextHopCode;
    }

    public String getBagStatus() {
        return bagStatus;
    }

    public void setBagStatus(String bagStatus) {
        this.bagStatus = bagStatus;
    }

    public List<String> getPreviewItemNos() {
        return previewItemNos;
    }

    public void setPreviewItemNos(List<String> previewItemNos) {
        this.previewItemNos = previewItemNos;
    }
}
