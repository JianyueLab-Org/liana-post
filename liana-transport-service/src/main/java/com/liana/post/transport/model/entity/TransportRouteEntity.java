package com.liana.post.transport.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("transport_route")
public class TransportRouteEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeCode;
    private String originFacilityId;
    private String destinationFacilityId;
    private String transportType;
    private BigDecimal estimatedHours;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getOriginFacilityId() { return originFacilityId; }
    public void setOriginFacilityId(String originFacilityId) { this.originFacilityId = originFacilityId; }
    public String getDestinationFacilityId() { return destinationFacilityId; }
    public void setDestinationFacilityId(String destinationFacilityId) { this.destinationFacilityId = destinationFacilityId; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
