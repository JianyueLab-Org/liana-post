package com.liana.post.dispatch.model.dto;

import java.util.List;

public class DispatchBatchCreateRequest {
    private String bagNo;
    private List<String> bagNos;
    private String routeCode;

    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public List<String> getBagNos() { return bagNos; }
    public void setBagNos(List<String> bagNos) { this.bagNos = bagNos; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
}
