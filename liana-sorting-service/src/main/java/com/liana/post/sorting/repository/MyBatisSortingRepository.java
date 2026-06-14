package com.liana.post.sorting.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.sorting.constant.SortingConstants;
import com.liana.post.sorting.mapper.DiscrepancyVerificationMapper;
import com.liana.post.sorting.mapper.PackageItemLineMapper;
import com.liana.post.sorting.mapper.SortingManifestItemMapper;
import com.liana.post.sorting.mapper.SortingManifestMapper;
import com.liana.post.sorting.mapper.SortingTotalPackageMapper;
import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.model.entity.PackageItemLineEntity;
import com.liana.post.sorting.model.entity.SortingManifestEntity;
import com.liana.post.sorting.model.entity.SortingManifestItemEntity;
import com.liana.post.sorting.model.entity.SortingTotalPackageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisSortingRepository implements SortingRepository {
    private final SortingTotalPackageMapper totalPackageMapper;
    private final SortingManifestMapper manifestMapper;
    private final SortingManifestItemMapper manifestItemMapper;
    private final PackageItemLineMapper packageItemLineMapper;
    private final DiscrepancyVerificationMapper discrepancyVerificationMapper;

    public MyBatisSortingRepository(SortingTotalPackageMapper totalPackageMapper,
                                    SortingManifestMapper manifestMapper,
                                    SortingManifestItemMapper manifestItemMapper,
                                    PackageItemLineMapper packageItemLineMapper,
                                    DiscrepancyVerificationMapper discrepancyVerificationMapper) {
        this.totalPackageMapper = totalPackageMapper;
        this.manifestMapper = manifestMapper;
        this.manifestItemMapper = manifestItemMapper;
        this.packageItemLineMapper = packageItemLineMapper;
        this.discrepancyVerificationMapper = discrepancyVerificationMapper;
    }

    @Override
    @Transactional
    public SortingTotalPackageEntity saveTotalPackage(SortingTotalPackageEntity entity) {
        stamp(entity);
        if (StringUtils.hasText(entity.getPackageNo()) == false) {
            entity.setPackageNo(IdGeneratorUtil.generateBagNo());
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0L);
        }
        if (totalPackageMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save sorting total package");
        }
        return entity;
    }

    @Override
    @Transactional
    public SortingTotalPackageEntity updateTotalPackage(SortingTotalPackageEntity entity) {
        if (entity.getId() == null) {
            throw new BusinessException(400, "sorting total package id cannot be blank");
        }
        stamp(entity);
        entity.setVersion(entity.getVersion() == null ? 0L : entity.getVersion() + 1);
        if (totalPackageMapper.updateById(entity) <= 0) {
            throw new BusinessException(500, "failed to update sorting total package");
        }
        return entity;
    }

    @Override
    public Optional<SortingTotalPackageEntity> findTotalPackageByNo(String packageNo) {
        return Optional.ofNullable(totalPackageMapper.selectOne(new LambdaQueryWrapper<SortingTotalPackageEntity>()
                .eq(SortingTotalPackageEntity::getPackageNo, normalize(packageNo))));
    }

    @Override
    public List<SortingTotalPackageEntity> findAllTotalPackages() {
        return totalPackageMapper.selectList(null);
    }

    @Override
    @Transactional
    public SortingManifestEntity saveManifest(SortingManifestEntity entity) {
        stamp(entity);
        if (StringUtils.hasText(entity.getManifestNo()) == false) {
            entity.setManifestNo(IdGeneratorUtil.generateDispatchNo());
        }
        int affected = entity.getId() == null ? manifestMapper.insert(entity) : manifestMapper.updateById(entity);
        if (affected <= 0) {
            throw new BusinessException(500, "failed to save manifest");
        }
        return entity;
    }

    @Override
    public Optional<SortingManifestEntity> findManifestByNo(String manifestNo) {
        return Optional.ofNullable(manifestMapper.selectOne(new LambdaQueryWrapper<SortingManifestEntity>()
                .eq(SortingManifestEntity::getManifestNo, normalize(manifestNo))));
    }

    @Override
    public List<SortingManifestEntity> findAllManifests() {
        return manifestMapper.selectList(null);
    }

    @Override
    public List<SortingManifestItemEntity> findManifestItems(String manifestNo) {
        return manifestItemMapper.selectList(new LambdaQueryWrapper<SortingManifestItemEntity>()
                .eq(SortingManifestItemEntity::getManifestNo, normalize(manifestNo)));
    }

    @Override
    @Transactional
    public SortingManifestItemEntity saveManifestItem(SortingManifestItemEntity entity) {
        stamp(entity);
        if (manifestItemMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save manifest item");
        }
        return entity;
    }

    @Override
    @Transactional
    public PackageItemLineEntity savePackageItemLine(PackageItemLineEntity entity) {
        stamp(entity);
        if (StringUtils.hasText(entity.getBizLineNo()) == false) {
            entity.setBizLineNo(IdGeneratorUtil.generateBizNo("PIL"));
        }
        if (StringUtils.hasText(entity.getIdempotencyKey()) == false) {
            throw new BusinessException(400, "idempotencyKey cannot be blank");
        }
        if (packageItemLineMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save package item line");
        }
        return entity;
    }

    @Override
    public List<PackageItemLineEntity> findPackageLinesByItem(String itemNo) {
        return packageItemLineMapper.selectList(new LambdaQueryWrapper<PackageItemLineEntity>()
                .eq(PackageItemLineEntity::getItemNo, normalize(itemNo))
                .orderByAsc(PackageItemLineEntity::getEventTime));
    }

    @Override
    public List<PackageItemLineEntity> findPackageLinesByPackage(String packageNo) {
        return packageItemLineMapper.selectList(new LambdaQueryWrapper<PackageItemLineEntity>()
                .and(w -> w.eq(PackageItemLineEntity::getFromPackageNo, normalize(packageNo))
                        .or().eq(PackageItemLineEntity::getToPackageNo, normalize(packageNo)))
                .orderByAsc(PackageItemLineEntity::getEventTime));
    }

    @Override
    public List<PackageItemLineEntity> findAllPackageLines() {
        return packageItemLineMapper.selectList(null);
    }

    @Override
    public List<SortingTotalPackageEntity> findTotalPackagesByManifest(String manifestNo) {
        return totalPackageMapper.selectList(new LambdaQueryWrapper<SortingTotalPackageEntity>()
                .eq(SortingTotalPackageEntity::getManifestNo, normalize(manifestNo))
                .orderByDesc(SortingTotalPackageEntity::getUpdatedAt));
    }

    @Override
    public List<SortingTotalPackageEntity> findTotalPackagesByDestination(String destinationOrgCode) {
        return totalPackageMapper.selectList(new LambdaQueryWrapper<SortingTotalPackageEntity>()
                .eq(SortingTotalPackageEntity::getDestinationOrgCode, normalize(destinationOrgCode))
                .in(SortingTotalPackageEntity::getPackageStatus,
                        SortingConstants.PACKAGE_STATUS_RAW,
                        SortingConstants.PACKAGE_STATUS_SEALED,
                        SortingConstants.PACKAGE_STATUS_RECEIVED,
                        SortingConstants.PACKAGE_STATUS_OPENING)
                .orderByAsc(SortingTotalPackageEntity::getPackageNo));
    }

    @Override
    public List<PackageItemLineEntity> findLatestLoadLinesBySlot(String slotCode) {
        return packageItemLineMapper.selectList(new LambdaQueryWrapper<PackageItemLineEntity>()
                .eq(PackageItemLineEntity::getTargetCenterCode, normalize(slotCode))
                .eq(PackageItemLineEntity::getActionType, SortingConstants.LINE_ACTION_LOAD)
                .orderByDesc(PackageItemLineEntity::getEventTime));
    }

    @Override
    @Transactional
    public DiscrepancyVerificationEntity saveDiscrepancy(DiscrepancyVerificationEntity entity) {
        stamp(entity);
        if (StringUtils.hasText(entity.getVerificationNo()) == false) {
            entity.setVerificationNo(IdGeneratorUtil.generateBizNo("DV"));
        }
        if (discrepancyVerificationMapper.insert(entity) <= 0) {
            throw new BusinessException(500, "failed to save discrepancy verification");
        }
        return entity;
    }

    @Override
    public List<DiscrepancyVerificationEntity> findDiscrepanciesByPackage(String packageNo) {
        return discrepancyVerificationMapper.selectList(new LambdaQueryWrapper<DiscrepancyVerificationEntity>()
                .eq(DiscrepancyVerificationEntity::getPackageNo, normalize(packageNo))
                .orderByDesc(DiscrepancyVerificationEntity::getCreatedAt));
    }

    @Override
    public List<DiscrepancyVerificationEntity> findAllDiscrepancies() {
        return discrepancyVerificationMapper.selectList(null);
    }

    @Override
    public boolean hasAnyData() {
        return totalPackageMapper.selectCount(null) > 0
                || manifestMapper.selectCount(null) > 0
                || manifestItemMapper.selectCount(null) > 0
                || packageItemLineMapper.selectCount(null) > 0
                || discrepancyVerificationMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        if (hasAnyData()) {
            return;
        }
        SortingTotalPackageEntity totalPackage = new SortingTotalPackageEntity();
        totalPackage.setPackageNo("PKG202606140001");
        totalPackage.setPackageLevel(SortingConstants.PACKAGE_LEVEL_TRANSIT);
        totalPackage.setPackageStatus(SortingConstants.PACKAGE_STATUS_SEALED);
        totalPackage.setSourceOrgCode("B1");
        totalPackage.setDestinationOrgCode("C1");
        totalPackage.setPrealertFlag(1);
        saveTotalPackage(totalPackage);
    }

    private void stamp(SortingTotalPackageEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(SortingManifestEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void stamp(SortingManifestItemEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
    }

    private void stamp(PackageItemLineEntity entity) {
        if (entity.getEventTime() == null) {
            entity.setEventTime(LocalDateTime.now());
        }
        if (entity.getLineStatus() == null || entity.getLineStatus().isBlank()) {
            entity.setLineStatus("VALID");
        }
    }

    private void stamp(DiscrepancyVerificationEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "code cannot be blank");
        }
        return value.trim().toUpperCase();
    }
}
