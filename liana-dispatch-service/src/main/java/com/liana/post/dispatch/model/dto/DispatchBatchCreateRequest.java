package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;

public class DispatchBatchCreateRequest {
    @NotBlank
    private String bagNo;
    private String routeCode;

    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
}