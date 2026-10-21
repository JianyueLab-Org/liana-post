package com.liana.post.dispatch.model.dto;

import java.util.ArrayList;
import java.util.List;

public class DispatchBatchResponse {
    private Long id;
    private String batchNo;
    private String bagNo;
    private List<String> bagNos = new ArrayList<>();
    private String routeCode;
    private String status;
    private Long approvedBy;
    private String approvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public List<String> getBagNos() { return bagNos; }
    public void setBagNos(List<String> bagNos) { this.bagNos = bagNos; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public String getApprovedAt() { return approvedAt; }
    public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
}
