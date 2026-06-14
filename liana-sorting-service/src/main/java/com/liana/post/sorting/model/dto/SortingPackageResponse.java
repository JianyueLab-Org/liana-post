package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingPackageResponse {
    private Long id;
    private String packageNo;
    private String packageLevel;
    private String packageStatus;
    private String sourceOrgCode;
    private String destinationOrgCode;
    private String parentPackageNo;
    private String manifestNo;
    private Integer prealertFlag;
    private LocalDateTime sealedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime openedAt;
    private String terminalReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public String getPackageLevel() { return packageLevel; }
    public void setPackageLevel(String packageLevel) { this.packageLevel = packageLevel; }
    public String getPackageStatus() { return packageStatus; }
    public void setPackageStatus(String packageStatus) { this.packageStatus = packageStatus; }
    public String getSourceOrgCode() { return sourceOrgCode; }
    public void setSourceOrgCode(String sourceOrgCode) { this.sourceOrgCode = sourceOrgCode; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String destinationOrgCode) { this.destinationOrgCode = destinationOrgCode; }
    public String getParentPackageNo() { return parentPackageNo; }
    public void setParentPackageNo(String parentPackageNo) { this.parentPackageNo = parentPackageNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public Integer getPrealertFlag() { return prealertFlag; }
    public void setPrealertFlag(Integer prealertFlag) { this.prealertFlag = prealertFlag; }
    public LocalDateTime getSealedAt() { return sealedAt; }
    public void setSealedAt(LocalDateTime sealedAt) { this.sealedAt = sealedAt; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public String getTerminalReason() { return terminalReason; }
    public void setTerminalReason(String terminalReason) { this.terminalReason = terminalReason; }
}
