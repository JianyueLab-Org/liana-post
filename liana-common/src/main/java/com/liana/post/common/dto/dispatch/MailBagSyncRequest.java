package com.liana.post.common.dto.dispatch;

import java.util.List;

public class MailBagSyncRequest {
    private String bagNo;
    private String currentFacilityCode;
    private List<String> waybillNos;

    public String getBagNo() {
        return bagNo;
    }

    public void setBagNo(String bagNo) {
        this.bagNo = bagNo;
    }

    public String getCurrentFacilityCode() {
        return currentFacilityCode;
    }

    public void setCurrentFacilityCode(String currentFacilityCode) {
        this.currentFacilityCode = currentFacilityCode;
    }

    public List<String> getWaybillNos() {
        return waybillNos;
    }

    public void setWaybillNos(List<String> waybillNos) {
        this.waybillNos = waybillNos;
    }
}
