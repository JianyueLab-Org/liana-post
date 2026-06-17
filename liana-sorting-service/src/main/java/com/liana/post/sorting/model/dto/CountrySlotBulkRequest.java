package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class CountrySlotBulkRequest {
    @NotBlank
    private String stationCode;
    @NotBlank
    private String countryCode;
    private String exportFacilityCode;
    private List<String> itemNos;
    private String operatorId;
    private String idempotencyKey;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getExportFacilityCode() { return exportFacilityCode; }
    public void setExportFacilityCode(String exportFacilityCode) { this.exportFacilityCode = exportFacilityCode; }
    public List<String> getItemNos() { return itemNos; }
    public void setItemNos(List<String> itemNos) { this.itemNos = itemNos; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
