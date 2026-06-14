package com.liana.post.sorting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sorting_total_package")
public class SortingTotalPackageEntity {
    @TableId(type = IdType.AUTO)
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
    private Long version;
    private String extra;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
