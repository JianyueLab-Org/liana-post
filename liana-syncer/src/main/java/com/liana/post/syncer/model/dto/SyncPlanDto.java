package com.liana.post.syncer.model.dto;

import java.util.List;

/**
 * 同步计划 DTO。
 */
public class SyncPlanDto {

    private String sourceService;
    private String targetService;
    private String bizType;
    private List<String> bizIds;

    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public List<String> getBizIds() { return bizIds; }
    public void setBizIds(List<String> bizIds) { this.bizIds = bizIds; }
}