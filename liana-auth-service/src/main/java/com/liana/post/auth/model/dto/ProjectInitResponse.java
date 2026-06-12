package com.liana.post.auth.model.dto;

import java.util.List;

public class ProjectInitResponse {
    private List<String> createdRoles;
    private List<String> skippedRoles;
    private List<String> createdPermissions;
    private List<String> skippedPermissions;
    private List<String> createdUsers;
    private List<String> skippedUsers;

    public List<String> getCreatedRoles() { return createdRoles; }
    public void setCreatedRoles(List<String> createdRoles) { this.createdRoles = createdRoles; }
    public List<String> getSkippedRoles() { return skippedRoles; }
    public void setSkippedRoles(List<String> skippedRoles) { this.skippedRoles = skippedRoles; }
    public List<String> getCreatedPermissions() { return createdPermissions; }
    public void setCreatedPermissions(List<String> createdPermissions) { this.createdPermissions = createdPermissions; }
    public List<String> getSkippedPermissions() { return skippedPermissions; }
    public void setSkippedPermissions(List<String> skippedPermissions) { this.skippedPermissions = skippedPermissions; }
    public List<String> getCreatedUsers() { return createdUsers; }
    public void setCreatedUsers(List<String> createdUsers) { this.createdUsers = createdUsers; }
    public List<String> getSkippedUsers() { return skippedUsers; }
    public void setSkippedUsers(List<String> skippedUsers) { this.skippedUsers = skippedUsers; }
}
