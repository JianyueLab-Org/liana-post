package com.liana.post.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liana.post.auth.constant.AuthConstants;
import com.liana.post.auth.mapper.PermissionMapper;
import com.liana.post.auth.mapper.RoleMapper;
import com.liana.post.auth.mapper.RolePermissionMapper;
import com.liana.post.auth.mapper.UserMapper;
import com.liana.post.auth.mapper.UserRoleMapper;
import com.liana.post.auth.model.entity.PermissionEntity;
import com.liana.post.auth.model.entity.RoleEntity;
import com.liana.post.auth.model.entity.RolePermissionEntity;
import com.liana.post.auth.model.entity.UserEntity;
import com.liana.post.auth.model.entity.UserRoleEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAuthRepository implements AuthRepository {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public MyBatisAuthRepository(UserMapper userMapper,
                                 RoleMapper roleMapper,
                                 PermissionMapper permissionMapper,
                                 UserRoleMapper userRoleMapper,
                                 RolePermissionMapper rolePermissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public Optional<UserEntity> findUserByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalize(username))
        ));
    }

    @Override
    public Optional<RoleEntity> findPrimaryRoleByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId)).stream()
                .findFirst()
                .map(userRole -> roleMapper.selectById(userRole.getRoleId()));
    }

    @Override
    public List<PermissionEntity> findPermissionsByUserId(Long userId) {
        List<PermissionEntity> permissions = new ArrayList<>();
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
        for (UserRoleEntity userRole : userRoles) {
            if (userRole == null || userRole.getRoleId() == null) {
                continue;
            }
            List<RolePermissionEntity> rolePermissions = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, userRole.getRoleId())
            );
            for (RolePermissionEntity rolePermission : rolePermissions) {
                if (rolePermission == null || rolePermission.getPermissionId() == null) {
                    continue;
                }
                PermissionEntity permission = permissionMapper.selectById(rolePermission.getPermissionId());
                if (permission != null) {
                    permissions.add(permission);
                }
            }
        }
        return permissions;
    }

    @Override
    public List<UserEntity> findAllUsers() {
        return userMapper.selectList(null);
    }

    @Override
    public List<RoleEntity> findAllRoles() {
        return roleMapper.selectList(null);
    }

    @Override
    public List<PermissionEntity> findAllPermissions() {
        return permissionMapper.selectList(null);
    }

    @Override
    public Optional<RoleEntity> findRoleByCode(String code) {
        return Optional.ofNullable(roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, normalize(code))));
    }

    @Override
    public Optional<PermissionEntity> findPermissionByCode(String code) {
        return Optional.ofNullable(permissionMapper.selectOne(new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, normalize(code))));
    }

    @Override
    public boolean existsUserByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalize(username))) > 0;
    }

    @Override
    public boolean existsRoleByCode(String code) {
        return roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, normalize(code))) > 0;
    }

    @Override
    public boolean existsPermissionByCode(String code) {
        return permissionMapper.selectCount(new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, normalize(code))) > 0;
    }

    @Override
    public UserEntity saveUser(UserEntity user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        return user;
    }

    @Override
    public RoleEntity saveRole(RoleEntity role) {
        if (role.getId() == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        return role;
    }

    @Override
    public PermissionEntity savePermission(PermissionEntity permission) {
        if (permission.getId() == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
        return permission;
    }

    @Override
    public boolean saveUserRole(Long userId, Long roleId) {
        boolean exists = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getRoleId, roleId)) > 0;
        if (exists) {
            return false;
        }
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(entity);
        return true;
    }

    @Override
    public boolean saveRolePermission(Long roleId, Long permissionId) {
        boolean exists = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermissionEntity>()
                .eq(RolePermissionEntity::getRoleId, roleId)
                .eq(RolePermissionEntity::getPermissionId, permissionId)) > 0;
        if (exists) {
            return false;
        }
        RolePermissionEntity entity = new RolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionId(permissionId);
        entity.setCreatedAt(LocalDateTime.now());
        rolePermissionMapper.insert(entity);
        return true;
    }

    @Override
    public void updatePassword(String username, String passwordHash) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalize(username)));
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordHash);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
