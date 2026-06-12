package com.liana.post.dispatch.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName("dispatch_bag")
public class DispatchBagEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bagNo;
    private String originFacilityCode;
    private String destinationFacilityCode;
    private String routeCode;
    private String transportTaskCode;
    private String mailNoList;
    private String mailTypeCode;
    private String status;
    private Integer mailCount;
    private Integer totalWeightGrams;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBagNo() { return bagNo; }
    public void setBagNo(String bagNo) { this.bagNo = bagNo; }
    public String getOriginFacilityCode() { return originFacilityCode; }
    public void setOriginFacilityCode(String originFacilityCode) { this.originFacilityCode = originFacilityCode; }
    public String getDestinationFacilityCode() { return destinationFacilityCode; }
    public void setDestinationFacilityCode(String destinationFacilityCode) { this.destinationFacilityCode = destinationFacilityCode; }
    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
    public String getTransportTaskCode() { return transportTaskCode; }
    public void setTransportTaskCode(String transportTaskCode) { this.transportTaskCode = transportTaskCode; }
    public String getMailNoList() { return mailNoList; }
    public void setMailNoList(String mailNoList) { this.mailNoList = mailNoList; }
    public String getMailTypeCode() { return mailTypeCode; }
    public void setMailTypeCode(String mailTypeCode) { this.mailTypeCode = mailTypeCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getMailCount() { return mailCount; }
    public void setMailCount(Integer mailCount) { this.mailCount = mailCount; }
    public Integer getTotalWeightGrams() { return totalWeightGrams; }
    public void setTotalWeightGrams(Integer totalWeightGrams) { this.totalWeightGrams = totalWeightGrams; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getMailNoListAsList() {
        if (mailNoList == null || mailNoList.isBlank()) {
            return new ArrayList<>();
        }
        return JSON.parseObject(mailNoList, new TypeReference<List<String>>() {});
    }

    public void setMailNoListAsList(List<String> mailNos) {
        this.mailNoList = JSON.toJSONString(mailNos == null ? List.of() : mailNos);
    }
}
