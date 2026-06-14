package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SortingRebagRequest {
    @NotBlank
    private String stationCode;
    @NotBlank
    private String routeCode;
    @NotBlank
    private String sourcePackageNo;
    @NotBlank
    private String packageLevel;
    @NotEmpty
    private List<String> itemNos;
    private String operatorId;
    private String idempotencyKey;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getSourcePackageNo() { return sourcePackageNo; }
    public void setSourcePackageNo(String sourcePackageNo) { this.sourcePackageNo = sourcePackageNo; }
    public String getPackageLevel() { return packageLevel; }
    public void setPackageLevel(String packageLevel) { this.packageLevel = packageLevel; }
    public List<String> getItemNos() { return itemNos; }
    public void setItemNos(List<String> itemNos) { this.itemNos = itemNos; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
