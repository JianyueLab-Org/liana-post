package com.liana.post.sorting.model.dto;

import java.util.ArrayList;
import java.util.List;

public class SortingUnpackPreviewResponse {
    private String packageNo;
    private String manifestNo;
    private String source;
    private List<String> itemNos = new ArrayList<>();

    public String getPackageNo() {
        return packageNo;
    }

    public void setPackageNo(String packageNo) {
        this.packageNo = packageNo;
    }

    public String getManifestNo() {
        return manifestNo;
    }

    public void setManifestNo(String manifestNo) {
        this.manifestNo = manifestNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getItemNos() {
        return itemNos;
    }

    public void setItemNos(List<String> itemNos) {
        this.itemNos = itemNos;
    }
}
