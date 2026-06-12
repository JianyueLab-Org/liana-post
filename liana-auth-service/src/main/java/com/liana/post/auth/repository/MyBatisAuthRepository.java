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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAuthRepository implements AuthRepository {

    private static final String DEFAULT_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

    @Override
    public boolean hasAnyData() {
        return userMapper.selectCount(null) > 0
                || roleMapper.selectCount(null) > 0
                || permissionMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional
    public void seedDefaults() {
        seedIfAbsent();
    }

    private void seedIfAbsent() {
        RoleEntity clerk = ensureRole(AuthConstants.ROLE_CLERK, "邮局营业员");
        RoleEntity manager = ensureRole(AuthConstants.ROLE_MANAGER, "邮局经理");
        RoleEntity sorter = ensureRole(AuthConstants.ROLE_SORTER, "转运中心操作员");
        RoleEntity admin = ensureRole(AuthConstants.ROLE_ADMIN, "系统管理员");

        PermissionEntity mailCreate = ensurePermission("MAIL_CREATE", "邮件收寄");
        PermissionEntity mailQuery = ensurePermission("MAIL_QUERY", "邮件查询");
        PermissionEntity trackQuery = ensurePermission("TRACK_QUERY", "轨迹查询");
        PermissionEntity tokenIssue = ensurePermission("TOKEN_ISSUE", "Token签发");
        PermissionEntity userAdmin = ensurePermission("USER_ADMIN", "用户管理");
        PermissionEntity roleAdmin = ensurePermission("ROLE_ADMIN", "角色管理");

        ensureUser("clerk001", DEFAULT_PASSWORD, "Namoa Clerk", "B1", 1, clerk, List.of(mailCreate, mailQuery, trackQuery));
        ensureUser("manager001", DEFAULT_PASSWORD, "Namoa Manager", "B1", 1, manager, List.of(mailQuery, trackQuery));
        ensureUser("sorter001", DEFAULT_PASSWORD, "Transfer Sorter", "A1", 1, sorter, List.of(mailQuery, trackQuery));
        ensureUser("admin001", DEFAULT_PASSWORD, "System Admin", "A1", 1, admin, List.of(mailCreate, mailQuery, trackQuery, tokenIssue, userAdmin, roleAdmin));
    }

    private RoleEntity ensureRole(String code, String name) {
        return findRoleByCode(code).orElseGet(() -> {
            RoleEntity role = new RoleEntity();
            role.setCode(code);
            role.setName(name);
            role.setDescription(name);
            role.setStatus(1);
            role.setCreatedAt(LocalDateTime.now());
            role.setUpdatedAt(LocalDateTime.now());
            return saveRole(role);
        });
    }

    private PermissionEntity ensurePermission(String code, String name) {
        return findPermissionByCode(code).orElseGet(() -> {
            PermissionEntity permission = new PermissionEntity();
            permission.setCode(code);
            permission.setName(name);
            permission.setDescription(name);
            permission.setStatus(1);
            permission.setCreatedAt(LocalDateTime.now());
            permission.setUpdatedAt(LocalDateTime.now());
            return savePermission(permission);
        });
    }

    private void ensureUser(String username,
                            String rawPassword,
                            String displayName,
                            String facilityCode,
                            Integer status,
                            RoleEntity role,
                            List<PermissionEntity> permissions) {
        UserEntity user = findUserByUsername(username).orElseGet(UserEntity::new);
        boolean isNew = user.getId() == null;
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setFacilityCode(facilityCode);
        user.setPhone(defaultPhone(username));
        user.setEmail(username + "@liana.post");
        user.setStatus(status);
        if (isNew) {
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setUpdatedAt(LocalDateTime.now());
        saveUser(user);
        saveUserRole(user.getId(), role.getId());
        for (PermissionEntity permission : permissions) {
            saveRolePermission(role.getId(), permission.getId());
        }
    }

    private String defaultPhone(String username) {
        if (username == null || username.length() < 3) {
            return null;
        }
        if (username.startsWith("clerk")) {
            return "13800000001";
        }
        if (username.startsWith("manager")) {
            return "13800000002";
        }
        if (username.startsWith("sorter")) {
            return "13800000003";
        }
        if (username.startsWith("admin")) {
            return "13800000004";
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
