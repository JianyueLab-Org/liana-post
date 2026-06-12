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
    @NotEmpty
    private List<String> mailNoList;

    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getDestinationFacilityCode() { return destinationFacilityCode; }
    public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public List<String> getMailNoList() { return mailNoList; }
    public void setMailNoList(List<String> mailNoList) { this.mailNoList = mailNoList; }
}