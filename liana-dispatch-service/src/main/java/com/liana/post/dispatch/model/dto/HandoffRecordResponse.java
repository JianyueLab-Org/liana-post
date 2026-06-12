package com.liana.post.dispatch.model.dto;

public class HandoffRecordResponse {
    private Long id;
    private String handoffNo;
    private String bagNo;
    private String fromFacilityCode;
    private String toFacilityCode;
    private String handoffTime;
    private Long receiverId;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHandoffNo() { return handoffNo; }
    public void setHandoffNo(String handoffNo) { this.handoffNo = handoffNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getFromFacilityCode() { return fromFacilityCode; }
    public void setFromFacilityCode(String fromFacilityCode) { this.fromFacilityCode = fromFacilityCode; }
    public String getToFacilityCode() { return toFacilityCode; }
    public void setToFacilityCode(String toFacilityCode) { this.toFacilityCode = toFacilityCode; }
    public String getHandoffTime() { return handoffTime; }
    public void setHandoffTime(String handoffTime) { this.handoffTime = handoffTime; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
