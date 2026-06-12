package com.liana.post.auth.service;

import com.liana.post.auth.model.dto.LoginRequest;
import com.liana.post.auth.model.dto.LoginResponse;
import com.liana.post.auth.model.dto.PermissionResponse;
import com.liana.post.auth.model.dto.ProjectInitResponse;
import com.liana.post.auth.model.dto.RoleResponse;
import com.liana.post.auth.model.dto.UserCreateRequest;
import com.liana.post.auth.model.dto.UserProfileResponse;
import com.liana.post.auth.model.dto.UserSummaryResponse;

import java.util.List;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    UserProfileResponse getProfile(String username);
    String validateToken(String token);
    List<UserSummaryResponse> listUsers();
    UserSummaryResponse createUser(UserCreateRequest request);
    void resetPassword(String username, String newPassword);
    List<RoleResponse> listRoles();
    List<PermissionResponse> listPermissions();
    ProjectInitResponse initProject();
}
