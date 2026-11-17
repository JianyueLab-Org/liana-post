package com.liana.post.transport.model.dto;

public class TransportTaskRequest {
    private String taskCode;
    private String manifestNo;
    private Long dispatchBagId;
    private Long assetId;
    private Long routeId;
    private Long scheduleId;
    private String status;

    public String getTaskCode() { return taskCode; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public Long getDispatchBagId() { return dispatchBagId; }
    public void setDispatchBagId(Long dispatchBagId) { this.dispatchBagId = dispatchBagId; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
