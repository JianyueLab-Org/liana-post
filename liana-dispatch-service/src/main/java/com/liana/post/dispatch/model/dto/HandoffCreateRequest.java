package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;

public class HandoffCreateRequest {
    @NotBlank
    private String bagNo;
    @NotBlank
    private String toFacilityCode;
    private Long receiverId;

    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getToFacilityCode() { return toFacilityCode; }
    public void setToFacilityCode(String toFacilityCode) { this.toFacilityCode = toFacilityCode; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
}
