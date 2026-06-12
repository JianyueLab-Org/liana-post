package com.liana.post.common.dto.dispatch;

public class MailDispatchCandidateResponse {
    private String waybillNo;
    private String mailTypeCode;
    private String status;
    private String currentFacilityCode;
    private String destFacilityCode;
    private Integer weightGrams;

    public String getWaybillNo() {
        return waybillNo;
    }

    public void setWaybillNo(String waybillNo) {
        this.waybillNo = waybillNo;
    }

    public String getMailTypeCode() {
        return mailTypeCode;
    }

    public void setMailTypeCode(String mailTypeCode) {
        this.mailTypeCode = mailTypeCode;
    }

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

    public String getDestFacilityCode() {
        return destFacilityCode;
    }

    public void setDestFacilityCode(String destFacilityCode) {
        this.destFacilityCode = destFacilityCode;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(Integer weightGrams) {
        this.weightGrams = weightGrams;
    }
}
