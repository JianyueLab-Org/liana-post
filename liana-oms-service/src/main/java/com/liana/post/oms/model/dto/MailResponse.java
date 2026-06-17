package com.liana.post.oms.model.dto;

import java.math.BigDecimal;

public class MailResponse {
    private Long id;
    private String waybillNo;
    private String bagNo;
    private String packageId;
    private String mailTypeCode;
    private String serviceType;
    private String mailScope;
    private String destCountryCode;
    private String status;
    private String senderFullName;
    private String recipientFullName;
    private String originFacilityCode;
    private String currentFacilityCode;
    private String currentSlot;
    private String destFacilityCode;
    private String destinationNode;
    private Integer weightGrams;
    private BigDecimal declaredValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWaybillNo() { return waybillNo; }
    public void setWaybillNo(String waybillNo) { this.waybillNo = waybillNo; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getMailScope() { return mailScope; }
    public void setMailScope(String mailScope) { this.mailScope = mailScope; }
    public String getDestCountryCode() { return destCountryCode; }
    public void setDestCountryCode(String destCountryCode) { this.destCountryCode = destCountryCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSenderFullName() { return senderFullName; }
    public void setSenderFullName(String senderFullName) { this.senderFullName = senderFullName; }
    public String getRecipientFullName() { return recipientFullName; }
    public void setRecipientFullName(String recipientFullName) { this.recipientFullName = recipientFullName; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getCurrentFacilityCode() { return currentFacilityCode; }
    public void setCurrentFacilityCode(String currentFacilityCode) { this.currentFacilityCode = currentFacilityCode; }
    public String getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(String currentSlot) { this.currentSlot = currentSlot; }
    public String getDestFacilityCode() { return destFacilityCode; }
    public void setDestFacilityCode(String destFacilityCode) { this.destFacilityCode = destFacilityCode; }
    public String getDestinationNode() { return destinationNode; }
    public void setDestinationNode(String destinationNode) { this.destinationNode = destinationNode; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(BigDecimal declaredValue) { this.declaredValue = declaredValue; }
}
