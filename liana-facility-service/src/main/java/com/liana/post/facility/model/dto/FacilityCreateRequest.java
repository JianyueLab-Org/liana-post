package com.liana.post.facility.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class FacilityCreateRequest {
    @NotBlank @Size(max = 64) private String facilityCode;
    @NotBlank @Size(max = 128) private String name;
    @NotBlank @Size(max = 64) private String typeCode;
    @Size(max = 64) private String parentFacilityCode;
    @Size(max = 8) private String countryCode;
    @Size(max = 64) private String province;
    @Size(max = 64) private String city;
    @Size(max = 255) private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    public String getFacilityCode() { return facilityCode; }
    public void setFacilityCode(String facilityCode) { this.facilityCode = facilityCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getParentFacilityCode() { return parentFacilityCode; }
    public void setParentFacilityCode(String parentFacilityCode) { this.parentFacilityCode = parentFacilityCode; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}