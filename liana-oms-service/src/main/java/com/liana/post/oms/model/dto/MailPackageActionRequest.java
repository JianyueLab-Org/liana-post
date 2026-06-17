package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailPackageActionRequest {
    @NotBlank
    private String packageId;
    private String currentFacilityCode;

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }
}
