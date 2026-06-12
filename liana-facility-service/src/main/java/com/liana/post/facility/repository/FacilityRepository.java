package com.liana.post.facility.repository;

import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;

import java.util.List;
import java.util.Optional;

public interface FacilityRepository {
    FacilityTypeEntity saveFacilityType(FacilityTypeEntity entity);
    FacilityEntity saveFacility(FacilityEntity entity);
    FacilityRouteEntity saveRoute(FacilityRouteEntity entity);
    FacilityRouteEntity updateRoute(FacilityRouteEntity entity);
    Optional<FacilityTypeEntity> findFacilityTypeByCode(String code);
    Optional<FacilityEntity> findFacilityByCode(String facilityCode);
    Optional<FacilityRouteEntity> findRouteByCode(String routeCode);
    List<FacilityTypeEntity> findAllFacilityTypes();
    List<FacilityEntity> findAllFacilities();
    List<FacilityRouteEntity> findAllRoutes();
    boolean hasAnyData();
}
