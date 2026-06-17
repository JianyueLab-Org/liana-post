package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailRouteAssignRequest {
    private String currentSlot;
    private String destinationNode;
    private String currentFacilityCode;
    private String status;

    public String getCurrentSlot() {
        return currentSlot;
    }

    public void setCurrentSlot(String currentSlot) {
        this.currentSlot = currentSlot;
    }

    public String getDestinationNode() {
        return destinationNode;
    }

    public void setDestinationNode(String destinationNode) {
        this.destinationNode = destinationNode;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
