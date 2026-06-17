package com.liana.post.common.dto.sorting;

public class ManifestArrivedRequest {
    private String manifestCode;
    private String totalPackageNo;
    private String stationCode;
    private String arrivedAt;
    private String remark;

    public String getManifestCode() {
        return manifestCode;
    }

    public void setManifestCode(String manifestCode) {
        this.manifestCode = manifestCode;
    }

    public String getTotalPackageNo() {
        return totalPackageNo;
    }

    public void setTotalPackageNo(String totalPackageNo) {
        this.totalPackageNo = totalPackageNo;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(String arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
