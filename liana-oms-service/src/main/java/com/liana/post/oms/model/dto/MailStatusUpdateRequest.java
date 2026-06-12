package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailStatusUpdateRequest {
    @NotBlank
    private String status;
    private String currentFacilityCode;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }
}