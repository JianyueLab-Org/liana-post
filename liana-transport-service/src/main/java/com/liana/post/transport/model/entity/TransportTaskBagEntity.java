package com.liana.post.transport.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("transport_task_bag")
public class TransportTaskBagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long dispatchBagId;
    private String bagNo;
    private Integer weightGrams;
    private LocalDateTime linkedAt;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getDispatchBagId() { return dispatchBagId; }
    public void setDispatchBagId(Long dispatchBagId) { this.dispatchBagId = dispatchBagId; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
    public void setLinkedAt(LocalDateTime linkedAt) { this.linkedAt = linkedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
