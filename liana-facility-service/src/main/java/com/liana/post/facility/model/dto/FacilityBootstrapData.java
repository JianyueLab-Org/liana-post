package com.liana.post.facility.model.dto;

import java.util.List;

public class FacilityBootstrapData {
    private List<FacilityTypeCreateRequest> facilityTypes;
    private List<FacilityCreateRequest> facilities;
    private List<FacilityRouteCreateRequest> routes;
    public List<FacilityTypeCreateRequest> getFacilityTypes() { return facilityTypes; }
    public void setFacilityTypes(List<FacilityTypeCreateRequest> facilityTypes) { this.facilityTypes = facilityTypes; }
    public List<FacilityCreateRequest> getFacilities() { return facilities; }
    public void setFacilities(List<FacilityCreateRequest> facilities) { this.facilities = facilities; }
    public List<FacilityRouteCreateRequest> getRoutes() { return routes; }
    public void setRoutes(List<FacilityRouteCreateRequest> routes) { this.routes = routes; }
}