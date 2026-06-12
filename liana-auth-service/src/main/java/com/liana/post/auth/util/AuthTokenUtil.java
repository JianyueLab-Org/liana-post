package com.liana.post.auth.util;

import com.liana.post.common.constant.AuthConstants;
import com.liana.post.common.util.JwtTokenUtil;

import java.util.HashMap;
import java.util.Map;

public final class AuthTokenUtil {
    private AuthTokenUtil() {}

    public static String buildToken(Long userId, String username, String displayName, String role, String facilityCode, java.util.List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AuthConstants.CLAIM_USER_ID, userId);
        claims.put(AuthConstants.CLAIM_DISPLAY_NAME, displayName);
        claims.put(AuthConstants.CLAIM_PERMISSIONS, permissions);
        return JwtTokenUtil.generateToken(username, role, facilityCode, claims);
    }
}