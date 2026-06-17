package com.liana.post.oms.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("mail")
public class MailEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String waybillNo;
    private String bagNo;
    private String packageId;
    private String mailTypeCode;
    private String serviceType;
    private String mailScope;
    private String destCountryCode;
    private Long senderId;
    private Long recipientId;
    private String originFacilityCode;
    private String currentFacilityCode;
    private String currentSlot;
    private String destFacilityCode;
    private String destinationNode;
    private String status;
    private Integer weightGrams;
    private BigDecimal declaredValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getWeightGrams() { return weightGrams; }
    public void setWeightGrams(Integer weightGrams) { this.weightGrams = weightGrams; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(BigDecimal declaredValue) { this.declaredValue = declaredValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
