package com.liana.post.dispatch.client;

import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "liana-facility-service", contextId = "dispatchFacilityRouteClient", url = "${facility.service.url:}")
public interface FacilityRouteClient {
    @GetMapping("/api/facilities/routes")
    Result<List<FacilityRouteView>> listRoutes();

    class FacilityRouteView {
        private String routeCode;
        private String originFacilityCode;
        private String destinationFacilityCode;
        private String transportMode;
        private Integer priorityLevel;
        private java.math.BigDecimal estimatedHours;
        private Integer status;
        public String getRouteCode() { return routeCode; }
        public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

        public String getOriginFacilityCode() { return originFacilityCode; }
        public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
        public String getDestinationFacilityCode() { return destinationFacilityCode; }
        public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public Integer getPriorityLevel() { return priorityLevel; }
        public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }
        public java.math.BigDecimal getEstimatedHours() { return estimatedHours; }
        public void setEstimatedHours(java.math.BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}
