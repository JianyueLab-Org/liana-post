package com.liana.post.auth.util;

import com.liana.post.auth.constant.AuthConstants;
import com.liana.post.auth.model.dto.LoginResponse;
import com.liana.post.auth.model.dto.PermissionResponse;
import com.liana.post.auth.model.dto.RoleResponse;
import com.liana.post.auth.model.dto.UserProfileResponse;
import com.liana.post.auth.model.dto.UserSummaryResponse;
import com.liana.post.auth.model.entity.PermissionEntity;
import com.liana.post.auth.model.entity.RoleEntity;
import com.liana.post.auth.model.entity.UserEntity;

import java.util.List;

public final class AuthMapper {
    private AuthMapper() {}

    public static LoginResponse toLoginResponse(UserEntity user, RoleEntity role, String token) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setRole(role == null ? null : role.getCode());
        response.setFacilityCode(user.getFacilityCode());
        return response;
    }

    public static UserProfileResponse toProfile(UserEntity user, RoleEntity role, List<PermissionEntity> permissions) {
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setFacilityCode(user.getFacilityCode());
        response.setRole(role == null ? AuthConstants.ROLE_CLERK : role.getCode());
        response.setPermissions(permissions == null ? List.of() : permissions.stream().map(PermissionEntity::getCode).toList());
        return response;
    }

    public static UserSummaryResponse toUserSummary(UserEntity user, RoleEntity role, List<PermissionEntity> permissions) {
        UserSummaryResponse response = new UserSummaryResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setFacilityCode(user.getFacilityCode());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setStatus(user.getStatus());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setRole(role == null ? null : role.getCode());
        response.setPermissions(permissions == null ? List.of() : permissions.stream().map(PermissionEntity::getCode).toList());
        return response;
    }

    public static RoleResponse toRoleResponse(RoleEntity role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setCode(role.getCode());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setStatus(role.getStatus());
        response.setCreatedAt(role.getCreatedAt());
        response.setUpdatedAt(role.getUpdatedAt());
        return response;
    }

    public static PermissionResponse toPermissionResponse(PermissionEntity permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setCode(permission.getCode());
        response.setName(permission.getName());
        response.setDescription(permission.getDescription());
        response.setStatus(permission.getStatus());
        response.setCreatedAt(permission.getCreatedAt());
        response.setUpdatedAt(permission.getUpdatedAt());
        return response;
    }
}
