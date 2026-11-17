package com.liana.post.facility.service.impl;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.facility.client.OmsClient;
import com.liana.post.facility.constant.FacilityConstants;
import com.liana.post.facility.model.dto.CountryResponse;
import com.liana.post.facility.model.dto.FacilityCreateRequest;
import com.liana.post.facility.model.dto.FacilityRouteCreateRequest;
import com.liana.post.facility.model.dto.FacilityTypeCreateRequest;
import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;
import com.liana.post.facility.mapper.DemoPostalNetworkMapper;
import com.liana.post.facility.repository.FacilityRepository;
import com.liana.post.facility.service.FacilityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FacilityServiceImpl implements FacilityService {

    private final FacilityRepository facilityRepository;
    private final DemoPostalNetworkMapper demoPostalNetworkMapper;
    private final ObjectProvider<OmsClient> omsClientProvider;

    public FacilityServiceImpl(FacilityRepository facilityRepository, DemoPostalNetworkMapper demoPostalNetworkMapper,
                               ObjectProvider<OmsClient> omsClientProvider) {
        this.facilityRepository = facilityRepository;
        this.demoPostalNetworkMapper = demoPostalNetworkMapper;
        this.omsClientProvider = omsClientProvider;
    }

    @Override
    public FacilityTypeEntity createFacilityType(FacilityTypeCreateRequest request) {
        FacilityTypeEntity entity = new FacilityTypeEntity();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        return facilityRepository.saveFacilityType(entity);
    }

    @Override
    public FacilityEntity createFacility(FacilityCreateRequest request) {
        if (!StringUtils.hasText(request.getTypeCode())) {
            throw new BusinessException(400, "facility typeCode is required");
        }
        if (facilityRepository.findFacilityTypeByCode(request.getTypeCode()).isEmpty()) {
            throw new BusinessException(404, "facility type not found: " + request.getTypeCode());
        }
        if (StringUtils.hasText(request.getParentFacilityCode())
                && facilityRepository.findFacilityByCode(request.getParentFacilityCode()).isEmpty()) {
            throw new BusinessException(404, "parent facility not found: " + request.getParentFacilityCode());
        }

        FacilityEntity entity = new FacilityEntity();
        entity.setFacilityCode(request.getFacilityCode());
        entity.setName(request.getName());
        entity.setTypeCode(request.getTypeCode());
        entity.setParentFacilityCode(request.getParentFacilityCode());
        entity.setCountryCode(StringUtils.hasText(request.getCountryCode())
                ? request.getCountryCode()
                : FacilityConstants.DEFAULT_COUNTRY_CODE);
        entity.setProvince(request.getProvince());
        entity.setCity(request.getCity());
        entity.setAddress(request.getAddress());
        entity.setLatitude(request.getLatitude());
        entity.setLongitude(request.getLongitude());
        entity.setStatus(1);
        return facilityRepository.saveFacility(entity);
    }

    @Override
    public FacilityRouteEntity createRoute(FacilityRouteCreateRequest request) {
        if (facilityRepository.findFacilityByCode(request.getOriginFacilityCode()).isEmpty()) {
            throw new BusinessException(404, "origin facility not found: " + request.getOriginFacilityCode());
        }
        String destinationCode = resolveRouteDestinationCode(request.getDestinationFacilityCode());

        FacilityRouteEntity entity = new FacilityRouteEntity();
        entity.setRouteCode(request.getRouteCode());
        entity.setOriginFacilityCode(request.getOriginFacilityCode());
        entity.setDestinationFacilityCode(destinationCode);
        entity.setTransportMode(request.getTransportMode());
        entity.setDistanceKm(request.getDistanceKm());
        entity.setEstimatedHours(request.getEstimatedHours());
        entity.setPriorityLevel(request.getPriorityLevel() == null ? 0 : request.getPriorityLevel());
        entity.setStatus(1);
        return facilityRepository.saveRoute(entity);
    }

    @Override
    public FacilityRouteEntity updateRoute(String routeCode, FacilityRouteCreateRequest request) {
        FacilityRouteEntity entity = facilityRepository.findRouteByCode(routeCode)
                .orElseThrow(() -> new BusinessException(404, "route not found: " + routeCode));
        if (facilityRepository.findFacilityByCode(request.getOriginFacilityCode()).isEmpty()) {
            throw new BusinessException(404, "origin facility not found: " + request.getOriginFacilityCode());
        }
        String destinationCode = resolveRouteDestinationCode(request.getDestinationFacilityCode());
        entity.setOriginFacilityCode(request.getOriginFacilityCode());
        entity.setDestinationFacilityCode(destinationCode);
        entity.setTransportMode(request.getTransportMode());
        entity.setDistanceKm(request.getDistanceKm());
        entity.setEstimatedHours(request.getEstimatedHours());
        entity.setPriorityLevel(request.getPriorityLevel() == null ? 0 : request.getPriorityLevel());
        return facilityRepository.updateRoute(entity);
    }

    @Override
    public FacilityTypeEntity getFacilityType(String code) {
        return facilityRepository.findFacilityTypeByCode(code)
                .orElseThrow(() -> new BusinessException(404, "facility type not found: " + code));
    }

    @Override
    public FacilityEntity getFacility(String facilityCode) {
        return facilityRepository.findFacilityByCode(facilityCode)
                .orElseThrow(() -> new BusinessException(404, "facility not found: " + facilityCode));
    }

    @Override
    public FacilityRouteEntity getRoute(String routeCode) {
        return facilityRepository.findRouteByCode(routeCode)
                .orElseThrow(() -> new BusinessException(404, "route not found: " + routeCode));
    }

    @Override
    public List<FacilityTypeEntity> listFacilityTypes() {
        return facilityRepository.findAllFacilityTypes();
    }

    @Override
    public List<FacilityEntity> listFacilities() {
        return facilityRepository.findAllFacilities();
    }

    @Override
    public List<FacilityRouteEntity> listRoutes() {
        return facilityRepository.findAllRoutes();
    }

    @Override
    public Map<String, Object> demoPostalNetwork() {
        return Map.of(
                "regions", demoPostalNetworkMapper.findRegions(),
                "islands", demoPostalNetworkMapper.findIslands(),
                "facilities", listFacilities(),
                "routes", listRoutes(),
                "facilityProfiles", demoPostalNetworkMapper.findFacilityProfiles(),
                "routeProfiles", demoPostalNetworkMapper.findRouteProfiles()
        );
    }

    @Override
    public DashboardSummaryResponse dashboardSummary() {
        List<FacilityEntity> facilities = listFacilities();
        List<FacilityTypeEntity> types = listFacilityTypes();
        List<FacilityRouteEntity> routes = listRoutes();
        long activeFacilities = facilities.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();
        long activeRoutes = routes.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("设施数据");
        response.setScope("全部机构");
        response.addMetric("机构数", facilities.size(), "facility 表", "info")
                .addMetric("启用机构", activeFacilities, "status = 1", "success")
                .addMetric("路线数", routes.size(), "facility_route 表", "neutral")
                .addMetric("机构类型", types.size(), "facility_type 表", "warning");
        response.addBreakdown("机构类型分布", countBy(facilities.stream().map(FacilityEntity::getTypeCode).collect(Collectors.toList())));
        response.addBreakdown("路线状态", List.of(
                DashboardSummaryResponse.item("启用", activeRoutes, "ACTIVE"),
                DashboardSummaryResponse.item("停用", routes.size() - activeRoutes, "DISABLED")
        ));
        return response;
    }

    private String resolveRouteDestinationCode(String destinationCode) {
        if (!StringUtils.hasText(destinationCode)) {
            throw new BusinessException(400, "destination code is required");
        }
        String normalized = destinationCode.trim();
        if (facilityRepository.findFacilityByCode(normalized).isPresent()) {
            return normalized;
        }
        if (isKnownCountryCode(normalized)) {
            return normalized.toUpperCase();
        }
        throw new BusinessException(404, "destination facility or country not found: " + normalized);
    }

    private List<DashboardSummaryResponse.BreakdownItem> countBy(List<String> values) {
        return values.stream()
                .map(value -> value == null || value.isBlank() ? "UNKNOWN" : value)
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> DashboardSummaryResponse.item(entry.getKey(), entry.getValue(), entry.getKey()))
                .toList();
    }

    private boolean isKnownCountryCode(String countryCode) {
        OmsClient client = omsClientProvider.getIfAvailable();
        if (client == null) {
            return false;
        }
        Result<List<CountryResponse>> result = client.listCountries();
        if (result == null || result.isFail() || result.getData() == null) {
            return false;
        }
        for (CountryResponse item : result.getData()) {
            if (item != null && countryCode.equalsIgnoreCase(item.getCode())) {
                return true;
            }
        }
        return false;
    }
}
