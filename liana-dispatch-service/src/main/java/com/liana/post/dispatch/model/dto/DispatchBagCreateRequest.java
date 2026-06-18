package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class DispatchBagCreateRequest {
    @NotBlank
    private String originFacilityCode;
    private String destinationFacilityCode;
    private String routeCode;
    @NotBlank
    private String mailTypeCode;
    private String mailScope;
    @NotEmpty
    private List<String> mailNoList;
    private String destCountryCode;
    private String actionMode;

    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getDestinationFacilityCode() { return destinationFacilityCode; }
    public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getMailScope() { return mailScope; }
    public void setMailScope(String mailScope) { this.mailScope = mailScope; }
    public List<String> getMailNoList() { return mailNoList; }
    public void setMailNoList(List<String> mailNoList) { this.mailNoList = mailNoList; }
    public String getDestCountryCode() { return destCountryCode; }
    public void setDestCountryCode(String destCountryCode) { this.destCountryCode = destCountryCode; }
    public String getActionMode() { return actionMode; }
    public void setActionMode(String actionMode) { this.actionMode = actionMode; }
}
