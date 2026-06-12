package com.liana.post.dispatch.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("handoff_record")
public class HandoffRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String handoffNo;
    private String bagNo;
    private String batchNo;
    private String fromFacilityCode;
    private String toFacilityCode;
    private LocalDateTime handoffTime;
    private Long receiverId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHandoffNo() { return handoffNo; }
    public void setHandoffNo(String handoffNo) { this.handoffNo = handoffNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getFromFacilityCode() { return fromFacilityCode; }
    public void setFromFacilityCode(String fromFacilityCode) { this.fromFacilityCode = fromFacilityCode; }
    public String getToFacilityCode() { return toFacilityCode; }
    public void setToFacilityCode(String toFacilityCode) { this.toFacilityCode = toFacilityCode; }
    public LocalDateTime getHandoffTime() { return handoffTime; }
    public void setHandoffTime(LocalDateTime handoffTime) { this.handoffTime = handoffTime; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
