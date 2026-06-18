package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SortingSecurityAuditResponse {
    private String itemNo;
    private String securityStatus;
    private String routeCode;
    private String nextHop;
    private String message;
    private String xrayImage;
    private String sourceImageName;
    private List<SortingDetectedLabelResponse> detectedLabels;
    private List<String> dangerLabels;
    private String mailScope;
    private String serviceType;
    private Boolean returnToOrigin;
    private String originFacilityCode;
    private LocalDateTime auditedAt;

    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getSecurityStatus() { return securityStatus; }
    public void setSecurityStatus(String securityStatus) { this.securityStatus = securityStatus; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getNextHop() { return nextHop; }
    public void setNextHop(String nextHop) { this.nextHop = nextHop; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getXrayImage() { return xrayImage; }
    public void setXrayImage(String xrayImage) { this.xrayImage = xrayImage; }
    public String getSourceImageName() { return sourceImageName; }
    public void setSourceImageName(String sourceImageName) { this.sourceImageName = sourceImageName; }
    public List<SortingDetectedLabelResponse> getDetectedLabels() { return detectedLabels; }
    public void setDetectedLabels(List<SortingDetectedLabelResponse> detectedLabels) { this.detectedLabels = detectedLabels; }
    public List<String> getDangerLabels() { return dangerLabels; }
    public void setDangerLabels(List<String> dangerLabels) { this.dangerLabels = dangerLabels; }
    public String getMailScope() { return mailScope; }
    public void setMailScope(String mailScope) { this.mailScope = mailScope; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public Boolean getReturnToOrigin() { return returnToOrigin; }
    public void setReturnToOrigin(Boolean returnToOrigin) { this.returnToOrigin = returnToOrigin; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public LocalDateTime getAuditedAt() { return auditedAt; }
    public void setAuditedAt(LocalDateTime auditedAt) { this.auditedAt = auditedAt; }
}
