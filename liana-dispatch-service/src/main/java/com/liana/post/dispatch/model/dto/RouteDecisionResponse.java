package com.liana.post.dispatch.model.dto;

public class RouteDecisionResponse {
    private String decision;
    private String reason;
    private String routeCode;
    private String sourceFacilityCode;
    private String targetFacilityCode;
    private String destCountryCode;
    private String mailTypeCode;
    private String routeScope;
    private String transportMode;
    private String exportFacilityCode;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getSourceFacilityCode() { return sourceFacilityCode; }
    public void setSourceFacilityCode(String sourceFacilityCode) { this.sourceFacilityCode = sourceFacilityCode; }
    public String getTargetFacilityCode() { return targetFacilityCode; }
    public void setTargetFacilityCode(String targetFacilityCode) { this.targetFacilityCode = targetFacilityCode; }
    public String getDestCountryCode() { return destCountryCode; }
    public void setDestCountryCode(String destCountryCode) { this.destCountryCode = destCountryCode; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getRouteScope() { return routeScope; }
    public void setRouteScope(String routeScope) { this.routeScope = routeScope; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public String getExportFacilityCode() { return exportFacilityCode; }
    public void setExportFacilityCode(String exportFacilityCode) { this.exportFacilityCode = exportFacilityCode; }
}
