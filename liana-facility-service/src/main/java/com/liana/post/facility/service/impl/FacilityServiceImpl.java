package com.liana.post.facility.service.impl;

import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.facility.client.OmsClient;
import com.liana.post.facility.constant.FacilityConstants;
import com.liana.post.facility.model.dto.CountryResponse;
import com.liana.post.facility.model.dto.FacilityBootstrapData;
import com.liana.post.facility.model.dto.FacilityCreateRequest;
import com.liana.post.facility.model.dto.FacilityRouteCreateRequest;
import com.liana.post.facility.model.dto.FacilityTypeCreateRequest;
import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;
import com.liana.post.facility.repository.FacilityRepository;
import com.liana.post.facility.service.FacilityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class FacilityServiceImpl implements FacilityService {

    private final FacilityRepository facilityRepository;
    private final ObjectProvider<OmsClient> omsClientProvider;

    public FacilityServiceImpl(FacilityRepository facilityRepository, ObjectProvider<OmsClient> omsClientProvider) {
        this.facilityRepository = facilityRepository;
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
    public FacilityBootstrapData bootstrapDefaults() {
        if (facilityRepository.hasAnyData()) {
            FacilityBootstrapData current = new FacilityBootstrapData();
            current.setFacilityTypes(facilityRepository.findAllFacilityTypes().stream().map(this::toTypeRequest).toList());
            current.setFacilities(facilityRepository.findAllFacilities().stream().map(this::toFacilityRequest).toList());
            current.setRoutes(facilityRepository.findAllRoutes().stream().map(this::toRouteRequest).toList());
            return current;
        }

        FacilityBootstrapData data = new FacilityBootstrapData();
        data.setFacilityTypes(List.of(
                type(FacilityConstants.TYPE_POST_OFFICE, "邮局", "基层邮政网点"),
                type(FacilityConstants.TYPE_TRANSFER_CENTER, "转运中心", "邮件集散与转运中心"),
                type(FacilityConstants.TYPE_INTERNATIONAL_GATEWAY, "国际交换局", "国际邮件交换节点"),
                type(FacilityConstants.TYPE_AIR_HUB, "航空节点", "空运中转节点"),
                type(FacilityConstants.TYPE_SEA_HUB, "海运节点", "海运中转节点")
        ));
        data.setFacilities(List.of(
                fac("A1", "Liana Prime", FacilityConstants.TYPE_TRANSFER_CENTER, null, "A1主枢纽"),
                fac("A2", "International Exchange Bureau", FacilityConstants.TYPE_INTERNATIONAL_GATEWAY, "A1", "A2国际互换局"),
                fac("B1", "Namoa Post Office", FacilityConstants.TYPE_POST_OFFICE, "A1", "B1支局"),
                fac("B2", "Taviri Post Office", FacilityConstants.TYPE_POST_OFFICE, "A1", "B2支局"),
                fac("B3", "Kelea Post Office", FacilityConstants.TYPE_POST_OFFICE, "A1", "B3支局"),
                fac("C1", "Oro Island Office", FacilityConstants.TYPE_POST_OFFICE, "B1", "C1岛屿网点"),
                fac("C2", "Miri Island Office", FacilityConstants.TYPE_POST_OFFICE, "B2", "C2岛屿网点"),
                fac("C3", "Sela Island Office", FacilityConstants.TYPE_POST_OFFICE, "B3", "C3岛屿网点")
        ));
        data.setRoutes(List.of(
                route("R-A1-B1", "A1", "B1", FacilityConstants.DEFAULT_TRANSPORT_MODE_LAND),
                route("R-A1-A2", "A1", "A2", FacilityConstants.DEFAULT_TRANSPORT_MODE_AIR),
                route("R-A2-A1", "A2", "A1", FacilityConstants.DEFAULT_TRANSPORT_MODE_AIR),
                route("R-A1-B2", "A1", "B2", FacilityConstants.DEFAULT_TRANSPORT_MODE_LAND),
                route("R-A1-B3", "A1", "B3", FacilityConstants.DEFAULT_TRANSPORT_MODE_LAND),
                route("R-B1-A1", "B1", "A1", FacilityConstants.DEFAULT_TRANSPORT_MODE_LAND)
        ));

        for (var request : data.getFacilityTypes()) {
            createFacilityType(request);
        }
        for (var request : data.getFacilities()) {
            createFacility(request);
        }
        for (var request : data.getRoutes()) {
            createRoute(request);
        }
        return data;
    }

    private FacilityTypeCreateRequest type(String code, String name, String description) {
        FacilityTypeCreateRequest request = new FacilityTypeCreateRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private FacilityCreateRequest fac(String facilityCode, String name, String typeCode, String parentFacilityCode, String address) {
        FacilityCreateRequest request = new FacilityCreateRequest();
        request.setFacilityCode(facilityCode);
        request.setName(name);
        request.setTypeCode(typeCode);
        request.setParentFacilityCode(parentFacilityCode);
        request.setCountryCode(FacilityConstants.DEFAULT_COUNTRY_CODE);
        request.setProvince("Liana Province");
        request.setCity("Liana City");
        request.setAddress(address);
        return request;
    }

    private FacilityRouteCreateRequest route(String routeCode, String origin, String destination, String transportMode) {
        FacilityRouteCreateRequest request = new FacilityRouteCreateRequest();
        request.setRouteCode(routeCode);
        request.setOriginFacilityCode(origin);
        request.setDestinationFacilityCode(destination);
        request.setTransportMode(transportMode);
        request.setPriorityLevel(0);
        return request;
    }

    private FacilityTypeCreateRequest toTypeRequest(FacilityTypeEntity entity) {
        FacilityTypeCreateRequest request = new FacilityTypeCreateRequest();
        request.setCode(entity.getCode());
        request.setName(entity.getName());
        request.setDescription(entity.getDescription());
        return request;
    }

    private FacilityCreateRequest toFacilityRequest(FacilityEntity entity) {
        FacilityCreateRequest request = new FacilityCreateRequest();
        request.setFacilityCode(entity.getFacilityCode());
        request.setName(entity.getName());
        request.setTypeCode(entity.getTypeCode());
        request.setParentFacilityCode(entity.getParentFacilityCode());
        request.setCountryCode(entity.getCountryCode());
        request.setProvince(entity.getProvince());
        request.setCity(entity.getCity());
        request.setAddress(entity.getAddress());
        request.setLatitude(entity.getLatitude());
        request.setLongitude(entity.getLongitude());
        return request;
    }

    private FacilityRouteCreateRequest toRouteRequest(FacilityRouteEntity entity) {
        FacilityRouteCreateRequest request = new FacilityRouteCreateRequest();
        request.setRouteCode(entity.getRouteCode());
        request.setOriginFacilityCode(entity.getOriginFacilityCode());
        request.setDestinationFacilityCode(entity.getDestinationFacilityCode());
        request.setTransportMode(entity.getTransportMode());
        request.setDistanceKm(entity.getDistanceKm());
        request.setEstimatedHours(entity.getEstimatedHours());
        request.setPriorityLevel(entity.getPriorityLevel());
        return request;
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
