package com.liana.post.oms.model.dto;

import java.time.LocalDateTime;

public class CountryResponse {
    private Long id;
    private String code;
    private String name;
    private String englishName;
    private Integer postalEnabled;
    private String upuRegion;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }
    public Integer getPostalEnabled() { return postalEnabled; }
    public void setPostalEnabled(Integer postalEnabled) { this.postalEnabled = postalEnabled; }
    public String getUpuRegion() { return upuRegion; }
    public void setUpuRegion(String upuRegion) { this.upuRegion = upuRegion; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
