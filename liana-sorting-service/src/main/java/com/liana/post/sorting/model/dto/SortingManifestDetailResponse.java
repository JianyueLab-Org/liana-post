package com.liana.post.sorting.model.dto;

import java.util.ArrayList;
import java.util.List;

public class SortingManifestDetailResponse extends SortingManifestResponse {
    private List<SortingManifestItemResponse> items = new ArrayList<>();

    public List<SortingManifestItemResponse> getItems() { return items; }
    public void setItems(List<SortingManifestItemResponse> items) { this.items = items; }
}
