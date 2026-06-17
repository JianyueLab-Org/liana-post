package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;

public class RouteDecisionRequest {
    @NotBlank
    private String originFacilityCode;
    @NotBlank
    private String mailTypeCode;
    @NotBlank
    private String destCountryCode;
    private String currentRouteCode;
    private String manualExportFacilityCode;
    private String actionMode;

    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getDestCountryCode() { return destCountryCode; }
    public void setDestCountryCode(String destCountryCode) { this.destCountryCode = destCountryCode; }
    public String getCurrentRouteCode() { return currentRouteCode; }
    public void setCurrentRouteCode(String currentRouteCode) { this.currentRouteCode = currentRouteCode; }
    public String getManualExportFacilityCode() { return manualExportFacilityCode; }
    public void setManualExportFacilityCode(String manualExportFacilityCode) { this.manualExportFacilityCode = manualExportFacilityCode; }
    public String getActionMode() { return actionMode; }
    public void setActionMode(String actionMode) { this.actionMode = actionMode; }
}
