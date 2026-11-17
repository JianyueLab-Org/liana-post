package com.liana.post.auth.service.impl;

import com.liana.post.auth.constant.AuthConstants;
import com.liana.post.auth.model.dto.LoginRequest;
import com.liana.post.auth.model.dto.LoginResponse;
import com.liana.post.auth.model.dto.PermissionResponse;
import com.liana.post.auth.model.dto.RoleResponse;
import com.liana.post.auth.model.dto.UserCreateRequest;
import com.liana.post.auth.model.dto.UserProfileResponse;
import com.liana.post.auth.model.dto.UserSummaryResponse;
import com.liana.post.auth.model.entity.PermissionEntity;
import com.liana.post.auth.model.entity.RoleEntity;
import com.liana.post.auth.model.entity.UserEntity;
import com.liana.post.auth.repository.AuthRepository;
import com.liana.post.auth.service.AuthService;
import com.liana.post.auth.util.AuthMapper;
import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthRepository authRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        UserEntity user = authRepository.findUserByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(404, "user not found: " + request.getUsername()));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "invalid username or password");
        }
        RoleEntity role = authRepository.findPrimaryRoleByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(403, "user role not found"));
        List<PermissionEntity> permissions = authRepository.findPermissionsByUserId(user.getId());

        Map<String, Object> claims = new HashMap<>();
        claims.put(AuthConstants.CLAIM_USER_ID, user.getId());
        claims.put(AuthConstants.CLAIM_DISPLAY_NAME, user.getDisplayName());
        claims.put(AuthConstants.CLAIM_PERMISSIONS, permissions.stream().map(PermissionEntity::getCode).toList());
        String token = JwtTokenUtil.generateToken(user.getUsername(), role.getCode(), user.getFacilityCode(), claims);
        return AuthMapper.toLoginResponse(user, role, token);
    }

    @Override
    public UserProfileResponse getProfile(String username) {
        UserEntity user = authRepository.findUserByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "user not found: " + username));
        RoleEntity role = authRepository.findPrimaryRoleByUserId(user.getId()).orElse(null);
        List<PermissionEntity> permissions = authRepository.findPermissionsByUserId(user.getId());
        return AuthMapper.toProfile(user, role, permissions);
    }

    @Override
    public String validateToken(String token) {
        return JwtTokenUtil.validateToken(token) ? "VALID" : "INVALID";
    }

    @Override
    public List<UserSummaryResponse> listUsers() {
        List<UserSummaryResponse> responses = new ArrayList<>();
        for (UserEntity user : authRepository.findAllUsers()) {
            RoleEntity role = authRepository.findPrimaryRoleByUserId(user.getId()).orElse(null);
            List<PermissionEntity> permissions = authRepository.findPermissionsByUserId(user.getId());
            responses.add(AuthMapper.toUserSummary(user, role, permissions));
        }
        return responses;
    }

    @Override
    public UserSummaryResponse createUser(UserCreateRequest request) {
        String username = normalize(request.getUsername());
        if (authRepository.existsUserByUsername(username)) {
            throw new BusinessException(409, "user already exists: " + username);
        }
        String roleCode = normalize(request.getRoleCode());
        final String resolvedRoleCode = "POSTOFFICE".equals(roleCode) ? AuthConstants.ROLE_CLERK : roleCode;
        RoleEntity role = authRepository.findRoleByCode(resolvedRoleCode)
                .orElseThrow(() -> new BusinessException(404, "role not found: " + resolvedRoleCode));

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setFacilityCode(request.getFacilityCode());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        authRepository.saveUser(user);
        authRepository.saveUserRole(user.getId(), role.getId());
        return AuthMapper.toUserSummary(user, role, authRepository.findPermissionsByUserId(user.getId()));
    }

    @Override
    public void resetPassword(String username, String newPassword) {
        if (!authRepository.existsUserByUsername(username)) {
            throw new BusinessException(404, "user not found: " + username);
        }
        authRepository.updatePassword(username, passwordEncoder.encode(newPassword));
    }

    @Override
    public List<RoleResponse> listRoles() {
        List<RoleResponse> responses = new ArrayList<>();
        for (RoleEntity role : authRepository.findAllRoles()) {
            if ("POSTOFFICE".equals(role.getCode())) {
                continue;
            }
            responses.add(AuthMapper.toRoleResponse(role));
        }
        return responses;
    }

    @Override
    public List<PermissionResponse> listPermissions() {
        List<PermissionResponse> responses = new ArrayList<>();
        for (PermissionEntity permission : authRepository.findAllPermissions()) {
            responses.add(AuthMapper.toPermissionResponse(permission));
        }
        return responses;
    }

    @Override
    public DashboardSummaryResponse dashboardSummary() {
        List<UserSummaryResponse> users = listUsers();
        List<RoleResponse> roles = listRoles();
        List<PermissionResponse> permissions = listPermissions();
        long activeUsers = users.stream().filter(user -> Integer.valueOf(1).equals(user.getStatus())).count();

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTitle("系统数据");
        response.setScope("全部机构");
        response.addMetric("用户数", users.size(), "账号表 user", "info")
                .addMetric("启用用户", activeUsers, "status = 1", "success")
                .addMetric("角色数", roles.size(), "role 表", "neutral")
                .addMetric("权限数", permissions.size(), "permission 表", "warning");
        response.addBreakdown("用户角色分布", countBy(users.stream()
                .map(UserSummaryResponse::getRole)
                .collect(Collectors.toList())));
        response.addBreakdown("用户状态", List.of(
                DashboardSummaryResponse.item("启用", activeUsers, "ACTIVE"),
                DashboardSummaryResponse.item("停用", users.size() - activeUsers, "DISABLED")
        ));
        return response;
    }

    private List<DashboardSummaryResponse.BreakdownItem> countBy(List<String> values) {
        return values.stream()
                .map(value -> value == null || value.isBlank() ? "UNASSIGNED" : value)
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> DashboardSummaryResponse.item(entry.getKey(), entry.getValue(), entry.getKey()))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
