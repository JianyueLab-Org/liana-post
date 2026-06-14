package com.liana.post.sorting.model.dto;

import java.time.LocalDateTime;

public class SortingManifestItemResponse {
    private Long id;
    private String manifestNo;
    private String itemNo;
    private String expectedPackageNo;
    private Integer expectedSeqNo;
    private String expectedRouteCode;
    private Integer expectedQty;
    private String itemStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManifestNo() { return manifestNo; }
    public void setManifestNo(String manifestNo) { this.manifestNo = manifestNo; }
    public String getItemNo() { return itemNo; }
    public void setItemNo(String itemNo) { this.itemNo = itemNo; }
    public String getExpectedPackageNo() { return expectedPackageNo; }
    public void setExpectedPackageNo(String expectedPackageNo) { this.expectedPackageNo = expectedPackageNo; }
    public Integer getExpectedSeqNo() { return expectedSeqNo; }
    public void setExpectedSeqNo(Integer expectedSeqNo) { this.expectedSeqNo = expectedSeqNo; }
    public String getExpectedRouteCode() { return expectedRouteCode; }
    public void setExpectedRouteCode(String expectedRouteCode) { this.expectedRouteCode = expectedRouteCode; }
    public Integer getExpectedQty() { return expectedQty; }
    public void setExpectedQty(Integer expectedQty) { this.expectedQty = expectedQty; }
    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
