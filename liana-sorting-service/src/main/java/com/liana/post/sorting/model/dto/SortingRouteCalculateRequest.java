package com.liana.post.sorting.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class SortingRouteCalculateRequest {
    @NotBlank
    private String stationCode;
    @NotBlank
    private String destinationOrgCode;
    private String serviceLevel;
    private List<String> itemNos;
    private Integer weightGram;
    private String volumeClass;
    private String idempotencyKey;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String destinationOrgCode) { this.destinationOrgCode = destinationOrgCode; }
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
    public List<String> getItemNos() { return itemNos; }
    public void setItemNos(List<String> itemNos) { this.itemNos = itemNos; }
    public Integer getWeightGram() { return weightGram; }
    public void setWeightGram(Integer weightGram) { this.weightGram = weightGram; }
    public String getVolumeClass() { return volumeClass; }
    public void setVolumeClass(String volumeClass) { this.volumeClass = volumeClass; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
