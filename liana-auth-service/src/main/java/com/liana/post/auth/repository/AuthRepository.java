package com.liana.post.auth.repository;

import com.liana.post.auth.model.entity.PermissionEntity;
import com.liana.post.auth.model.entity.RoleEntity;
import com.liana.post.auth.model.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface AuthRepository {
    Optional<UserEntity> findUserByUsername(String username);
    Optional<RoleEntity> findPrimaryRoleByUserId(Long userId);
    List<PermissionEntity> findPermissionsByUserId(Long userId);
    List<UserEntity> findAllUsers();
    List<RoleEntity> findAllRoles();
    List<PermissionEntity> findAllPermissions();
    Optional<RoleEntity> findRoleByCode(String code);
    Optional<PermissionEntity> findPermissionByCode(String code);
    boolean existsUserByUsername(String username);
    boolean existsRoleByCode(String code);
    boolean existsPermissionByCode(String code);
    UserEntity saveUser(UserEntity user);
    RoleEntity saveRole(RoleEntity role);
    PermissionEntity savePermission(PermissionEntity permission);
    boolean saveUserRole(Long userId, Long roleId);
    boolean saveRolePermission(Long roleId, Long permissionId);
    void updatePassword(String username, String passwordHash);
    boolean hasAnyData();
    void seedDefaults();
}
