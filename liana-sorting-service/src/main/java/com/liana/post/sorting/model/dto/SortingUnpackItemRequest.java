package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SortingUnpackItemRequest {
    @NotBlank
    private String stationCode;
    @NotBlank
    private String packageNo;
    private String manifestNo;
    @NotBlank
    private String scanMode;
    private String operatorId;
    private String deviceId;
    private String scanBatchNo;
    private String idempotencyKey;
    @NotEmpty
    private List<SortingUnpackItemEntry> items;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getScanMode() { return scanMode; }
    public void setScanMode(String scanMode) { this.scanMode = scanMode; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getScanBatchNo() { return scanBatchNo; }
    public void setScanBatchNo(String scanBatchNo) { this.scanBatchNo = scanBatchNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public List<SortingUnpackItemEntry> getItems() { return items; }
    public void setItems(List<SortingUnpackItemEntry> items) { this.items = items; }

    public static class SortingUnpackItemEntry {
        @NotBlank
        private String itemNo;
        private String routeCode;

        public String getItemNo() { return itemNo; }
        public void setItemNo(String itemNo) { this.itemNo = itemNo; }
        public String getRouteCode() { return routeCode; }
        public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    }
}
