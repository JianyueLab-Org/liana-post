package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class MailCreateRequest {
    private String originFacilityCode;
    private String currentFacilityCode;
    @NotBlank
    private String mailTypeCode;
    @NotBlank
    private String senderFullName;
    @NotBlank
    private String senderPhone;
    private String senderIdType;
    private String senderIdNumber;
    @NotBlank
    private String senderAddress;
    private String senderPostcode;
    @NotBlank
    private String recipientFullName;
    @NotBlank
    private String recipientPhone;
    @NotBlank
    private String recipientAddress;
    private String recipientPostcode;
    private String destCountryCode;
    @NotNull
    private Integer weightGrams;
    private BigDecimal declaredValue;
    private String serviceType;
    private String mailScope;

    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getCurrentFacilityCode() { return currentFacilityCode; }
    public void setCurrentFacilityCode(String currentFacilityCode) { this.currentFacilityCode = currentFacilityCode; }
    public String getSenderFullName() { return senderFullName; }
    public void setSenderFullName(String senderFullName) { this.senderFullName = senderFullName; }
    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }
    public String getSenderIdType() { return senderIdType; }
    public void setSenderIdType(String senderIdType) { this.senderIdType = senderIdType; }
    public String getSenderIdNumber() { return senderIdNumber; }
    public void setSenderIdNumber(String senderIdNumber) { this.senderIdNumber = senderIdNumber; }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress; }
    public String getSenderPostcode() { return senderPostcode; }
    public void setSenderPostcode(String senderPostcode) { this.senderPostcode = senderPostcode; }
    public String getRecipientFullName() { return recipientFullName; }
    public void setRecipientFullName(String recipientFullName) { this.recipientFullName = recipientFullName; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public String getRecipientPostcode() { return recipientPostcode; }
    public void setRecipientPostcode(String recipientPostcode) { this.recipientPostcode = recipientPostcode; }
    public String getDestCountryCode() { return destCountryCode; }
    public void setDestCountryCode(String destCountryCode) { this.destCountryCode = destCountryCode; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(BigDecimal declaredValue) { this.declaredValue = declaredValue; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getMailScope() { return mailScope; }
    public void setMailScope(String mailScope) { this.mailScope = mailScope; }
}
