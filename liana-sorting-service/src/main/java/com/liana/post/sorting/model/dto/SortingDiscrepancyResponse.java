package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingDiscrepancyResponse {
    private Long id;
    private String verificationNo;
    private String manifestNo;
    private String packageNo;
    private String itemNo;
    private String discrepancyType;
    private String discrepancySource;
    private Integer expectedQty;
    private Integer actualQty;
    private String exceptionLevel;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVerificationNo() { return verificationNo; }
    public void setVerificationNo(String verificationNo) { this.verificationNo = verificationNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }
    public String getDiscrepancySource() { return discrepancySource; }
    public void setDiscrepancySource(String discrepancySource) { this.discrepancySource = discrepancySource; }
    public Integer getExpectedQty() { return expectedQty; }
    public void setExpectedQty(Integer expectedQty) { this.expectedQty = expectedQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public String getExceptionLevel() { return exceptionLevel; }
    public void setExceptionLevel(String exceptionLevel) { this.exceptionLevel = exceptionLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
