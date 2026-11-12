package com.liana.post.facility.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DemoPostalNetworkMapper {
    @Select("SELECT region_code AS regionCode, region_name AS regionName, postcode_prefix AS postcodePrefix, " +
            "center_facility_code AS centerFacilityCode, inhabited_islands AS inhabitedIslands, " +
            "coverage_description AS coverageDescription FROM demo_postal_region ORDER BY region_code")
    List<Map<String, Object>> findRegions();

    @Select("SELECT island_code AS islandCode, region_code AS regionCode, island_name AS islandName, " +
            "postcode_range AS postcodeRange, island_type AS islandType, map_x AS mapX, map_y AS mapY " +
            "FROM demo_postal_island ORDER BY region_code, island_code")
    List<Map<String, Object>> findIslands();

    @Select("SELECT facility_code AS facilityCode, island_code AS islandCode, postcode_coverage AS postcodeCoverage, " +
            "service_coverage AS serviceCoverage, capacity_description AS capacityDescription, " +
            "opening_hours AS openingHours, map_x AS mapX, map_y AS mapY " +
            "FROM demo_facility_profile ORDER BY facility_code")
    List<Map<String, Object>> findFacilityProfiles();

    @Select("SELECT route_code AS routeCode, frequency_description AS frequencyDescription, " +
            "operating_note AS operatingNote FROM demo_route_profile ORDER BY route_code")
    List<Map<String, Object>> findRouteProfiles();
}
