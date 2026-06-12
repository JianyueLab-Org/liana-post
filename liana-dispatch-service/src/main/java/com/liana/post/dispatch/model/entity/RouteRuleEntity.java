package com.liana.post.dispatch.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("route_rule")
public class RouteRuleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String sourceFacilityCode;
    private String targetFacilityCode;
    private Integer priorityLevel;
    private String transportMode;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getSourceFacilityCode() { return sourceFacilityCode; }
    public void setSourceFacilityCode(String sourceFacilityCode) { this.sourceFacilityCode = sourceFacilityCode; }
    public String getTargetFacilityCode() { return targetFacilityCode; }
    public void setTargetFacilityCode(String targetFacilityCode) { this.targetFacilityCode = targetFacilityCode; }
    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}