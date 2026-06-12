package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class DispatchMailSyncRequest {
    @NotBlank
    private String bagNo;
    private String currentFacilityCode;
    @NotEmpty
    private List<String> mailNoList;

    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getCurrentFacilityCode() { return currentFacilityCode; }
    public void setCurrentFacilityCode(String currentFacilityCode) { this.currentFacilityCode = currentFacilityCode; }
    public List<String> getMailNoList() { return mailNoList; }
    public void setMailNoList(List<String> mailNoList) { this.mailNoList = mailNoList; }
}