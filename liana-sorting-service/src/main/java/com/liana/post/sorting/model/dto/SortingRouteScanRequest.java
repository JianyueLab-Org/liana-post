package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;

public class SortingRouteScanRequest {
    @NotBlank
    private String stationCode;
    @NotBlank
    private String itemNo;
    private String operatorId;
    private String deviceId;
    private String idempotencyKey;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
