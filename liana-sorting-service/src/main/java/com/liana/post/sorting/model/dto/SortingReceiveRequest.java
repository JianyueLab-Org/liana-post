package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SortingReceiveRequest {
    @NotBlank
    private String stationCode;
    private String manifestNo;
    @NotEmpty
    private List<String> packageNos;
    @NotBlank
    private String receiveMode;
    private String operatorId;
    private String idempotencyKey;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public List<String> getPackageNos() { return packageNos; }
    public void setPackageNos(List<String> packageNos) { this.packageNos = packageNos; }
    public String getReceiveMode() { return receiveMode; }
    public void setReceiveMode(String receiveMode) { this.receiveMode = receiveMode; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
