package com.liana.post.sorting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("discrepancy_verification")
public class DiscrepancyVerificationEntity {
    @TableId(type = IdType.AUTO)
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
    private String evidence;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolverId;
    private String remark;

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
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolverId() { return resolverId; }
    public void setResolverId(String resolverId) { this.resolverId = resolverId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
