package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingSecurityAuditResponse {
    private String itemNo;
    private String securityStatus;
    private String routeCode;
    private String nextHop;
    private String message;
    private String xrayImage;
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
    public LocalDateTime getAuditedAt() { return auditedAt; }
    public void setAuditedAt(LocalDateTime auditedAt) { this.auditedAt = auditedAt; }
}
