package com.liana.post.sorting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sorting_manifest_package")
public class SortingManifestPackageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String manifestNo;
    private String packageNo;
    private Integer expectedSeqNo;
    private BigDecimal expectedWeight;
    private BigDecimal actualWeight;
    private String sealNo;
    private String itemStatus;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }
    public Integer getExpectedSeqNo() { return expectedSeqNo; }
    public void setExpectedSeqNo(Integer expectedSeqNo) { this.expectedSeqNo = expectedSeqNo; }
    public BigDecimal getExpectedWeight() { return expectedWeight; }
    public void setExpectedWeight(BigDecimal expectedWeight) { this.expectedWeight = expectedWeight; }
    public BigDecimal getActualWeight() { return actualWeight; }
    public void setActualWeight(BigDecimal actualWeight) { this.actualWeight = actualWeight; }
    public String getSealNo() { return sealNo; }
    public void setSealNo(String sealNo) { this.sealNo = sealNo; }
    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
