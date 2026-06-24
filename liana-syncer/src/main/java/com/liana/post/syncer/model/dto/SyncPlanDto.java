package com.liana.post.syncer.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * 同步计划 DTO。
 */
public class SyncPlanDto {

    @NotBlank(message = "sourceService is required")
    private String sourceService;
    @NotBlank(message = "targetService is required")
    private String targetService;
    @NotBlank(message = "bizType is required")
    private String bizType;
    @NotEmpty(message = "bizIds is required")
    private List<String> bizIds;
    private Boolean failFirstAttempt;
    private Boolean alwaysFail;
    private Map<String, Object> attributes;

    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public List<String> getBizIds() { return bizIds; }
    public void setBizIds(List<String> bizIds) { this.bizIds = bizIds; }
    public Boolean getFailFirstAttempt() { return failFirstAttempt; }
    public void setFailFirstAttempt(Boolean failFirstAttempt) { this.failFirstAttempt = failFirstAttempt; }
    public Boolean getAlwaysFail() { return alwaysFail; }
    public void setAlwaysFail(Boolean alwaysFail) { this.alwaysFail = alwaysFail; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
