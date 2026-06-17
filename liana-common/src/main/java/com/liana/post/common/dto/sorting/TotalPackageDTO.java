package com.liana.post.common.dto.sorting;

import java.math.BigDecimal;
import java.util.List;

public class TotalPackageDTO {
    private String packageNo;
    private String packageLevel;
    private String routeCode;
    private BigDecimal packageWeight;
    private List<ManifestItemDTO> items;

    public String getPackageNo() {
        return packageNo;
    }

    public void setPackageNo(String packageNo) {
        this.packageNo = packageNo;
    }

    public String getPackageLevel() {
        return packageLevel;
    }

    public void setPackageLevel(String packageLevel) {
        this.packageLevel = packageLevel;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public BigDecimal getPackageWeight() {
        return packageWeight;
    }

    public void setPackageWeight(BigDecimal packageWeight) {
        this.packageWeight = packageWeight;
    }

    public List<ManifestItemDTO> getItems() {
        return items;
    }

    public void setItems(List<ManifestItemDTO> items) {
        this.items = items;
    }

    public static class ManifestItemDTO {
        private String itemNo;
        private Integer seqNo;
        private String routeCode;
        private BigDecimal weight;

        public String getItemNo() {
            return itemNo;
        }

        public void setItemNo(String itemNo) {
            this.itemNo = itemNo;
        }

        public Integer getSeqNo() {
            return seqNo;
        }

        public void setSeqNo(Integer seqNo) {
            this.seqNo = seqNo;
        }

        public String getRouteCode() {
            return routeCode;
        }

        public void setRouteCode(String routeCode) {
            this.routeCode = routeCode;
        }

        public BigDecimal getWeight() {
            return weight;
        }

        public void setWeight(BigDecimal weight) {
            this.weight = weight;
        }
    }
}
