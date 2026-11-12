package com.liana.post.facility.controller;

import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.model.Result;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.facility.model.dto.FacilityCreateRequest;
import com.liana.post.facility.model.dto.FacilityRouteCreateRequest;
import com.liana.post.facility.model.dto.FacilityTypeCreateRequest;
import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;
import com.liana.post.facility.service.FacilityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {
    private final FacilityService facilityService;
    public FacilityController(FacilityService facilityService) { this.facilityService = facilityService; }
    @PostMapping("/types")
    public Result<FacilityTypeEntity> createFacilityType(@Valid @RequestBody FacilityTypeCreateRequest request) { return Result.ok(facilityService.createFacilityType(request)); }
    @GetMapping("/types")
    public Result<List<FacilityTypeEntity>> listFacilityTypes() { return Result.ok(facilityService.listFacilityTypes()); }
    @GetMapping("/types/{code}")
    public Result<FacilityTypeEntity> getFacilityType(@PathVariable("code") String code) { return Result.ok(facilityService.getFacilityType(code)); }
    @PostMapping
    public Result<FacilityEntity> createFacility(@Valid @RequestBody FacilityCreateRequest request) { return Result.ok(facilityService.createFacility(request)); }
    @GetMapping
    public Result<List<FacilityEntity>> listFacilities() { return Result.ok(facilityService.listFacilities()); }
    @GetMapping("/{facilityCode}")
    public Result<FacilityEntity> getFacility(@PathVariable("facilityCode") String facilityCode) { return Result.ok(facilityService.getFacility(facilityCode)); }
    @PostMapping("/routes")
    public Result<FacilityRouteEntity> createRoute(@Valid @RequestBody FacilityRouteCreateRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        requireAdministrator(authorization);
        return Result.ok(facilityService.createRoute(request));
    }
    @PutMapping("/routes/{routeCode}")
    public Result<FacilityRouteEntity> updateRoute(@PathVariable("routeCode") String routeCode,
            @Valid @RequestBody FacilityRouteCreateRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        requireAdministrator(authorization);
        return Result.ok(facilityService.updateRoute(routeCode, request));
    }
    @GetMapping("/routes")
    public Result<List<FacilityRouteEntity>> listRoutes() { return Result.ok(facilityService.listRoutes()); }
    @GetMapping("/demo-network")
    public Result<Map<String, Object>> demoPostalNetwork() { return Result.ok(facilityService.demoPostalNetwork()); }
    @GetMapping("/routes/{routeCode}")
    public Result<FacilityRouteEntity> getRoute(@PathVariable("routeCode") String routeCode) { return Result.ok(facilityService.getRoute(routeCode)); }
    @GetMapping("/dashboard/summary")
    public Result<DashboardSummaryResponse> dashboardSummary() { return Result.ok(facilityService.dashboardSummary()); }

    private void requireAdministrator(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        if (!JwtTokenUtil.validateToken(token) || !"ADMIN".equalsIgnoreCase(JwtTokenUtil.getRoleFromToken(token))) {
            throw new BusinessException(403, "administrator required");
        }
    }
}
