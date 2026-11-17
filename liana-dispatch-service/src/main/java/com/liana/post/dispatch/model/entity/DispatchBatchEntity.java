package com.liana.post.dispatch.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import java.util.List;

@TableName("dispatch_batch")
public class DispatchBatchEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String manifestNo;
    private String sourceOrgCode;
    private String destinationOrgCode;
    private String facilityRouteCode;
    private String bagNo;
    @TableField(exist = false)
    private List<String> bagNos;
    private String routeCode;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getSourceOrgCode() { return sourceOrgCode; }
    public void setSourceOrgCode(String value) { sourceOrgCode = value; }
    public String getDestinationOrgCode() { return destinationOrgCode; }
    public void setDestinationOrgCode(String value) { destinationOrgCode = value; }
    public String getFacilityRouteCode() { return facilityRouteCode; }
    public void setFacilityRouteCode(String value) { facilityRouteCode = value; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public List<String> getBagNos() { return bagNos; }
    public void setBagNos(List<String> bagNos) { this.bagNos = bagNos; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
