package com.liana.post.common.dto.dispatch;

public class DispatchBagBriefResponse {
    private Long dispatchBagId;
    private String bagNo;
    private String originFacilityCode;
    private String destinationFacilityCode;
    private String routeCode;
    private String transportTaskCode;
    private String status;
    private String mailTypeCode;
    private Integer mailCount;
    private Integer totalWeightGrams;

    public Long getDispatchBagId() {
        return dispatchBagId;
    }

    public void setDispatchBagId(Long dispatchBagId) {
        this.dispatchBagId = dispatchBagId;
    }

    public String getBagNo() {
        return bagNo;
    }

    public void setBagNo(String bagNo) {
        this.bagNo = bagNo;
    }

    public String getOriginFacilityCode() {
        return originFacilityCode;
    }

    public void setOriginFacilityCode(String originFacilityCode) {
        this.originFacilityCode = originFacilityCode;
    }

    public String getDestinationFacilityCode() {
        return destinationFacilityCode;
    }

    public void setDestinationFacilityCode(String destinationFacilityCode) {
        this.destinationFacilityCode = destinationFacilityCode;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getTransportTaskCode() {
        return transportTaskCode;
    }

    public void setTransportTaskCode(String transportTaskCode) {
        this.transportTaskCode = transportTaskCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMailTypeCode() {
        return mailTypeCode;
    }

    public void setMailTypeCode(String mailTypeCode) {
        this.mailTypeCode = mailTypeCode;
    }

    public Integer getMailCount() {
        return mailCount;
    }

    public void setMailCount(Integer mailCount) {
        this.mailCount = mailCount;
    }

    public Integer getTotalWeightGrams() {
        return totalWeightGrams;
    }

    public void setTotalWeightGrams(Integer totalWeightGrams) {
        this.totalWeightGrams = totalWeightGrams;
    }
}
