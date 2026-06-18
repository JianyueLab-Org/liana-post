package com.liana.post.facility.service;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.facility.model.dto.FacilityBootstrapData;
import com.liana.post.facility.model.dto.FacilityCreateRequest;
import com.liana.post.facility.model.dto.FacilityRouteCreateRequest;
import com.liana.post.facility.model.dto.FacilityTypeCreateRequest;
import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;

import java.util.List;

public interface FacilityService {
    FacilityTypeEntity createFacilityType(FacilityTypeCreateRequest request);
    FacilityEntity createFacility(FacilityCreateRequest request);
    FacilityRouteEntity createRoute(FacilityRouteCreateRequest request);
    FacilityRouteEntity updateRoute(String routeCode, FacilityRouteCreateRequest request);
    FacilityTypeEntity getFacilityType(String code);
    FacilityEntity getFacility(String facilityCode);
    FacilityRouteEntity getRoute(String routeCode);
    List<FacilityTypeEntity> listFacilityTypes();
    List<FacilityEntity> listFacilities();
    List<FacilityRouteEntity> listRoutes();
    DashboardSummaryResponse dashboardSummary();
    FacilityBootstrapData bootstrapDefaults();
}
