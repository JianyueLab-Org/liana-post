package com.liana.post.sorting.model.dto;

public class SortingRouteResponse {
    private String routeCode;
    private String cellCode;
    private String nextPackageNo;
    private String decision;

    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getCellCode() { return cellCode; }
    public void setCellCode(String cellCode) { this.cellCode = cellCode; }
    public String getNextPackageNo() { return nextPackageNo; }
    public void setNextPackageNo(String nextPackageNo) { this.nextPackageNo = nextPackageNo; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
}
