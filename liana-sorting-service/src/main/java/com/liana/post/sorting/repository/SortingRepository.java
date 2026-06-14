package com.liana.post.sorting.repository;

import com.liana.post.sorting.model.entity.DiscrepancyVerificationEntity;
import com.liana.post.sorting.model.entity.PackageItemLineEntity;
import com.liana.post.sorting.model.entity.SortingManifestEntity;
import com.liana.post.sorting.model.entity.SortingManifestItemEntity;
import com.liana.post.sorting.model.entity.SortingTotalPackageEntity;

import java.util.List;
import java.util.Optional;

public interface SortingRepository {
    SortingTotalPackageEntity saveTotalPackage(SortingTotalPackageEntity entity);
    SortingTotalPackageEntity updateTotalPackage(SortingTotalPackageEntity entity);
    Optional<SortingTotalPackageEntity> findTotalPackageByNo(String packageNo);
    List<SortingTotalPackageEntity> findAllTotalPackages();
    SortingManifestEntity saveManifest(SortingManifestEntity entity);
    Optional<SortingManifestEntity> findManifestByNo(String manifestNo);
    List<SortingManifestEntity> findAllManifests();
    List<SortingManifestItemEntity> findManifestItems(String manifestNo);
    SortingManifestItemEntity saveManifestItem(SortingManifestItemEntity entity);
    PackageItemLineEntity savePackageItemLine(PackageItemLineEntity entity);
    List<PackageItemLineEntity> findPackageLinesByItem(String itemNo);
    List<PackageItemLineEntity> findPackageLinesByPackage(String packageNo);
    List<PackageItemLineEntity> findAllPackageLines();
    List<SortingTotalPackageEntity> findTotalPackagesByManifest(String manifestNo);
    List<SortingTotalPackageEntity> findTotalPackagesByDestination(String destinationOrgCode);
    List<PackageItemLineEntity> findLatestLoadLinesBySlot(String slotCode);
    DiscrepancyVerificationEntity saveDiscrepancy(DiscrepancyVerificationEntity entity);
    List<DiscrepancyVerificationEntity> findDiscrepanciesByPackage(String packageNo);
    List<DiscrepancyVerificationEntity> findAllDiscrepancies();
    boolean hasAnyData();
    void seedDefaults();
}
