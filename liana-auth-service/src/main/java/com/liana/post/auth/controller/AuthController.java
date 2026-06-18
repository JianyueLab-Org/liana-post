package com.liana.post.auth.controller;

import com.liana.post.auth.model.dto.LoginRequest;
import com.liana.post.auth.model.dto.LoginResponse;
import com.liana.post.auth.model.dto.PermissionResponse;
import com.liana.post.auth.model.dto.PasswordResetRequest;
import com.liana.post.auth.model.dto.ProjectInitResponse;
import com.liana.post.auth.model.dto.RoleResponse;
import com.liana.post.auth.model.dto.UserCreateRequest;
import com.liana.post.auth.model.dto.UserProfileResponse;
import com.liana.post.auth.model.dto.UserSummaryResponse;
import com.liana.post.common.dto.dashboard.DashboardSummaryResponse;
import com.liana.post.auth.service.AuthService;
import com.liana.post.common.model.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/profile")
    public Result<UserProfileResponse> profile(@RequestParam("username") String username) {
        return Result.ok(authService.getProfile(username));
    }

    @GetMapping("/token/validate")
    public Result<String> validate(@RequestHeader("Authorization") String authorization) {
        String token = authorization == null ? null : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        return Result.ok(authService.validateToken(token));
    }

    @GetMapping({"/users", "/system/users"})
    public Result<List<UserSummaryResponse>> users() {
        return Result.ok(authService.listUsers());
    }

    @PostMapping({"/users", "/system/users"})
    public Result<UserSummaryResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(authService.createUser(request));
    }

    @PostMapping({"/users/password/reset", "/system/users/password/reset"})
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getUsername(), request.getNewPassword());
        return Result.ok();
    }

    @GetMapping({"/roles", "/system/roles"})
    public Result<List<RoleResponse>> roles() {
        return Result.ok(authService.listRoles());
    }

    @GetMapping({"/permissions", "/system/permissions"})
    public Result<List<PermissionResponse>> permissions() {
        return Result.ok(authService.listPermissions());
    }

    @PostMapping({"/project/init", "/system/project/init"})
    public Result<ProjectInitResponse> initProject() {
        return Result.ok(authService.initProject());
    }

    @GetMapping({"/dashboard/summary", "/system/dashboard/summary"})
    public Result<DashboardSummaryResponse> dashboardSummary() {
        return Result.ok(authService.dashboardSummary());
    }
}
