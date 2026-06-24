package com.liana.post.sorting.model.dto;

import java.util.ArrayList;
import java.util.List;

public class SortingManifestDetailResponse extends SortingManifestResponse {
    private List<SortingManifestItemResponse> items = new ArrayList<>();
    private List<SortingPackageResponse> packages = new ArrayList<>();

    public List<SortingManifestItemResponse> getItems() { return items; }
    public void setItems(List<SortingManifestItemResponse> items) { this.items = items; }
    public List<SortingPackageResponse> getPackages() { return packages; }
    public void setPackages(List<SortingPackageResponse> packages) { this.packages = packages; }
}
