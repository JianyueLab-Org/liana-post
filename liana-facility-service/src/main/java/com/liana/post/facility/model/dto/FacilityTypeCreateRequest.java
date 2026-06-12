package com.liana.post.facility.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FacilityTypeCreateRequest {
    @NotBlank @Size(max = 64) private String code;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 255) private String description;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}