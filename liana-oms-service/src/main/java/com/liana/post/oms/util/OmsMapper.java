package com.liana.post.oms.util;

import com.liana.post.oms.model.dto.MailTypeResponse;
import com.liana.post.oms.model.dto.CountryResponse;
import com.liana.post.oms.model.dto.ServiceTypeResponse;
import com.liana.post.oms.model.entity.CountryEntity;
import com.liana.post.oms.model.entity.MailTypeEntity;
import com.liana.post.oms.model.entity.ServiceTypeEntity;

public final class OmsMapper {
    private OmsMapper() {}

    public static MailTypeResponse toMailTypeResponse(MailTypeEntity entity) {
        MailTypeResponse response = new MailTypeResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setRequiresSignature(entity.getRequiresSignature());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public static CountryResponse toCountryResponse(CountryEntity entity) {
        CountryResponse response = new CountryResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setEnglishName(entity.getEnglishName());
        response.setPostalEnabled(entity.getPostalEnabled());
        response.setUpuRegion(entity.getUpuRegion());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public static ServiceTypeResponse toServiceTypeResponse(ServiceTypeEntity entity) {
        ServiceTypeResponse response = new ServiceTypeResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setEnabled(entity.getEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
