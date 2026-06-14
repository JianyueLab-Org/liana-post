package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingManifestResponse {
    private Long id;
    private String manifestNo;
    private String manifestType;
    private String sourceOrgCode;
    private String destinationOrgCode;
    private String batchNo;
    private String manifestStatus;
    private Integer prealertFlag;
    private Integer expectedPackageQty;
    private Integer expectedItemQty;
    private LocalDateTime etaTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getManifestType() { return manifestType; }
    public void setManifestType(String manifestType) { this.manifestType = manifestType; }
    public String getSourceOrgCode() { return sourceOrgCode; }
    public void setSourceOrgCode(String sourceOrgCode) { this.sourceOrgCode = sourceOrgCode; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String destinationOrgCode) { this.destinationOrgCode = destinationOrgCode; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getManifestStatus() { return manifestStatus; }
    public void setManifestStatus(String manifestStatus) { this.manifestStatus = manifestStatus; }
    public Integer getPrealertFlag() { return prealertFlag; }
    public void setPrealertFlag(Integer prealertFlag) { this.prealertFlag = prealertFlag; }
    public Integer getExpectedPackageQty() { return expectedPackageQty; }
    public void setExpectedPackageQty(Integer expectedPackageQty) { this.expectedPackageQty = expectedPackageQty; }
    public Integer getExpectedItemQty() { return expectedItemQty; }
    public void setExpectedItemQty(Integer expectedItemQty) { this.expectedItemQty = expectedItemQty; }
    public LocalDateTime getEtaTime() { return etaTime; }
    public void setEtaTime(LocalDateTime etaTime) { this.etaTime = etaTime; }
}
