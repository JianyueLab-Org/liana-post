package com.liana.post.sorting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("package_item_line")
public class PackageItemLineEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizLineNo;
    private String idempotencyKey;
    private String itemNo;
    private String actionType;
    private String eventType;
    private String scanMode;
    private String fromPackageNo;
    private String toPackageNo;
    private Long parentLineId;
    private String manifestNo;
    private String scanBatchNo;
    private String stationCode;
    private String sourceCenterCode;
    private String targetCenterCode;
    private String operatorId;
    private String deviceId;
    private LocalDateTime eventTime;
    private String lineStatus;
    private String ext;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBizLineNo() { return bizLineNo; }
    public void setBizLineNo(String bizLineNo) { this.bizLineNo = bizLineNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getScanMode() { return scanMode; }
    public void setScanMode(String scanMode) { this.scanMode = scanMode; }
    public String getFromPackageNo() { return fromPackageNo; }
    public void setFromPackageNo(String fromPackageNo) { this.fromPackageNo = fromPackageNo; }
    public String getToPackageNo() { return toPackageNo; }
    public void setToPackageNo(String toPackageNo) { this.toPackageNo = toPackageNo; }
    public Long getParentLineId() { return parentLineId; }
    public void setParentLineId(Long parentLineId) { this.parentLineId = parentLineId; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getScanBatchNo() { return scanBatchNo; }
    public void setScanBatchNo(String scanBatchNo) { this.scanBatchNo = scanBatchNo; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getSourceCenterCode() { return sourceCenterCode; }
    public void setSourceCenterCode(String sourceCenterCode) { this.sourceCenterCode = sourceCenterCode; }
    public String getTargetCenterCode() { return targetCenterCode; }
    public void setTargetCenterCode(String targetCenterCode) { this.targetCenterCode = targetCenterCode; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLineStatus() { return lineStatus; }
    public void setLineStatus(String lineStatus) { this.lineStatus = lineStatus; }
    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }
}
