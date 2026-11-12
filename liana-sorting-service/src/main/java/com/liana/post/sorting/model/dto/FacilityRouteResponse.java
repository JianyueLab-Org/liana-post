package com.liana.post.sorting.model.dto;

import java.math.BigDecimal;

public class FacilityRouteResponse {
    private String routeCode;
    private String originFacilityCode;
    private String destinationFacilityCode;
    private String transportMode;
    private BigDecimal estimatedHours;
    private Integer priorityLevel;
    private Integer status;
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getDestinationFacilityCode() { return destinationFacilityCode; }
    public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
