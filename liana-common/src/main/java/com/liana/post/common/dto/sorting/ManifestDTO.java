package com.liana.post.common.dto.sorting;

import java.math.BigDecimal;
import java.util.List;

public class ManifestDTO {
    private String manifestCode;
    private String originNode;
    private String targetNode;
    private BigDecimal totalWeight;
    private List<TotalPackageDTO> totalPackages;

    public String getManifestCode() {
        return manifestCode;
    }

    public void setManifestCode(String manifestCode) {
        this.manifestCode = manifestCode;
    }

    public String getOriginNode() {
        return originNode;
    }

    public void setOriginNode(String originNode) {
        this.originNode = originNode;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(String targetNode) {
        this.targetNode = targetNode;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
    }

    public List<TotalPackageDTO> getTotalPackages() {
        return totalPackages;
    }

    public void setTotalPackages(List<TotalPackageDTO> totalPackages) {
        this.totalPackages = totalPackages;
    }
}
