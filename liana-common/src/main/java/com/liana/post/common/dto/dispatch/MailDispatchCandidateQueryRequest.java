package com.liana.post.common.dto.dispatch;

public class MailDispatchCandidateQueryRequest {
    private String currentFacilityCode;
    private String destFacilityCode;
    private String mailTypeCode;
    private String mailScope;
    private String destCountryCode;

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

    public String getMailTypeCode() {
        return mailTypeCode;
    }

    public void setMailTypeCode(String mailTypeCode) {
        this.mailTypeCode = mailTypeCode;
    }

    public String getMailScope() {
        return mailScope;
    }

    public void setMailScope(String mailScope) {
        this.mailScope = mailScope;
    }

    public String getDestCountryCode() {
        return destCountryCode;
    }

    public void setDestCountryCode(String destCountryCode) {
        this.destCountryCode = destCountryCode;
    }
}
