package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailSlotSealRequest {
    @NotBlank
    private String packageId;
    @NotBlank
    private String destinationNode;
    private String currentFacilityCode;
    private String status;

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
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
