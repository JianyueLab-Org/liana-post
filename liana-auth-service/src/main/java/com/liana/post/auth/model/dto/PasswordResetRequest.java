package com.liana.post.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String newPassword;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}