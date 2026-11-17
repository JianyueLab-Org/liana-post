package com.liana.post.dispatch.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("dispatch_batch_bag")
public class DispatchBatchBagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String bagNo;
    private Integer activeFlag;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Integer getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Integer value) { activeFlag = value; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
