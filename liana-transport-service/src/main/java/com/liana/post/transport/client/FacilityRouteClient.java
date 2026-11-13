package com.liana.post.transport.client;

import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "liana-facility-service", contextId = "transportFacilityRouteClient", url = "${facility.service.url:}")
public interface FacilityRouteClient {
    @GetMapping("/api/facilities/routes/{routeCode}")
    Result<FacilityRouteView> getRoute(@PathVariable("routeCode") String routeCode);

    class FacilityRouteView {
        private String routeCode;
        private String originFacilityCode;
        private String destinationFacilityCode;
        private String transportMode;
        private Integer status;
        public String getRouteCode() { return routeCode; }
        public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
        public String getOriginFacilityCode() { return originFacilityCode; }
        public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
        public String getDestinationFacilityCode() { return destinationFacilityCode; }
        public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}
