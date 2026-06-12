package com.liana.post.facility.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.facility.mapper.FacilityMapper;
import com.liana.post.facility.mapper.FacilityRouteMapper;
import com.liana.post.facility.mapper.FacilityTypeMapper;
import com.liana.post.facility.model.entity.FacilityEntity;
import com.liana.post.facility.model.entity.FacilityRouteEntity;
import com.liana.post.facility.model.entity.FacilityTypeEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisFacilityRepository implements FacilityRepository {

    private final FacilityTypeMapper facilityTypeMapper;
    private final FacilityMapper facilityMapper;
    private final FacilityRouteMapper facilityRouteMapper;

    public MyBatisFacilityRepository(FacilityTypeMapper facilityTypeMapper,
                                     FacilityMapper facilityMapper,
                                     FacilityRouteMapper facilityRouteMapper) {
        this.facilityTypeMapper = facilityTypeMapper;
        this.facilityMapper = facilityMapper;
        this.facilityRouteMapper = facilityRouteMapper;
    }

    @Override
    public FacilityTypeEntity saveFacilityType(FacilityTypeEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        int inserted = facilityTypeMapper.insert(entity);
        if (inserted <= 0) {
            throw new BusinessException(500, "failed to insert facility type");
        }
        return entity;
    }

    @Override
    public FacilityEntity saveFacility(FacilityEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCountryCode() == null || entity.getCountryCode().isBlank()) {
            entity.setCountryCode("LN");
        }
        int inserted = facilityMapper.insert(entity);
        if (inserted <= 0) {
            throw new BusinessException(500, "failed to insert facility");
        }
        return entity;
    }

    @Override
    public FacilityRouteEntity saveRoute(FacilityRouteEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        int inserted = facilityRouteMapper.insert(entity);
        if (inserted <= 0) {
            throw new BusinessException(500, "failed to insert route");
        }
        return entity;
    }

    @Override
    public FacilityRouteEntity updateRoute(FacilityRouteEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        int updated = facilityRouteMapper.updateById(entity);
        if (updated <= 0) {
            throw new BusinessException(500, "failed to update route");
        }
        return entity;
    }

    @Override
    public Optional<FacilityTypeEntity> findFacilityTypeByCode(String code) {
        return Optional.ofNullable(facilityTypeMapper.selectOne(new LambdaQueryWrapper<FacilityTypeEntity>().eq(FacilityTypeEntity::getCode, normalize(code))));
    }

    @Override
    public Optional<FacilityEntity> findFacilityByCode(String facilityCode) {
        return Optional.ofNullable(facilityMapper.selectOne(new LambdaQueryWrapper<FacilityEntity>().eq(FacilityEntity::getFacilityCode, normalize(facilityCode))));
    }

    @Override
    public Optional<FacilityRouteEntity> findRouteByCode(String routeCode) {
        return Optional.ofNullable(facilityRouteMapper.selectOne(new LambdaQueryWrapper<FacilityRouteEntity>().eq(FacilityRouteEntity::getRouteCode, normalize(routeCode))));
    }

    @Override
    public List<FacilityTypeEntity> findAllFacilityTypes() {
        return facilityTypeMapper.selectList(null);
    }

    @Override
    public List<FacilityEntity> findAllFacilities() {
        return facilityMapper.selectList(null);
    }

    @Override
    public List<FacilityRouteEntity> findAllRoutes() {
        return facilityRouteMapper.selectList(null);
    }

    @Override
    public boolean hasAnyData() {
        return facilityTypeMapper.selectCount(null) > 0
                || facilityMapper.selectCount(null) > 0
                || facilityRouteMapper.selectCount(null) > 0;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim();
    }
}
