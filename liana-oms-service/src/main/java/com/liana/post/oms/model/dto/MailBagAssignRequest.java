package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailBagAssignRequest {
    @NotBlank
    private String bagNo;
    private String currentFacilityCode;

    public String getBagNo() {
        return bagNo;
    }

    public void setBagNo(String bagNo) {
        this.bagNo = bagNo;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }
}