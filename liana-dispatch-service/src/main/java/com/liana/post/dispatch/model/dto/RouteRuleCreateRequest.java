package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RouteRuleCreateRequest {
    @NotBlank
    private String ruleCode;
    @NotBlank
    private String sourceFacilityCode;
    @NotBlank
    private String targetFacilityCode;
    @NotBlank
    private String transportMode;
    @NotNull
    private Integer priorityLevel;

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getSourceFacilityCode() { return sourceFacilityCode; }
    public void setSourceFacilityCode(String sourceFacilityCode) { this.sourceFacilityCode = sourceFacilityCode; }
    public String getTargetFacilityCode() { return targetFacilityCode; }
    public void setTargetFacilityCode(String targetFacilityCode) { this.targetFacilityCode = targetFacilityCode; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
}