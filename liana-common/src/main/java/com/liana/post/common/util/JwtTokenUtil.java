package com.liana.post.common.util;

import com.liana.post.common.constant.AuthConstants;
import com.liana.post.common.model.JwtUserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT Token 工具类。
 */
public final class JwtTokenUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenUtil.class);
    private static final String SECRET = "LianaPostSecureJwtSecretKeyCustom32Bytes!";
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000L;
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtTokenUtil() {
    }

    public static String generateToken(String username, String role, Map<String, Object> additionalClaims) {
        return generateToken(username, role, null, additionalClaims);
    }

    public static String generateToken(String username, String role, String facilityCode) {
        return generateToken(username, role, facilityCode, null);
    }

    public static String generateToken(String username,
                                       String role,
                                       String facilityCode,
                                       Map<String, Object> additionalClaims) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put(AuthConstants.CLAIM_USERNAME, username);
            claims.put(AuthConstants.CLAIM_ROLE, role);
            if (facilityCode != null && !facilityCode.trim().isEmpty()) {
                claims.put(AuthConstants.CLAIM_FACILITY_CODE, facilityCode.trim());
            }
            if (additionalClaims != null && !additionalClaims.isEmpty()) {
                claims.putAll(additionalClaims);
            }

            Date now = new Date();
            Date expirationDate = new Date(now.getTime() + EXPIRATION_TIME);

            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(AuthConstants.JWT_SUBJECT_PREFIX + ":" + username)
                    .setIssuer(AuthConstants.JWT_ISSUER)
                    .setIssuedAt(now)
                    .setExpiration(expirationDate)
                    .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                    .compact();
        } catch (Exception exception) {
            LOGGER.error("生成 JWT Token 失败，用户名: {}, 角色: {}, 错误信息: {}", username, role, exception.getMessage(), exception);
            throw new RuntimeException("Token generation failed", exception);
        }
    }

    public static String generateToken(JwtUserContext userContext, Map<String, Object> additionalClaims) {
        if (userContext == null) {
            throw new IllegalArgumentException("用户上下文不能为空");
        }
        Map<String, Object> mergedClaims = additionalClaims == null ? new HashMap<>() : new HashMap<>(additionalClaims);
        if (userContext.getUserId() != null) {
            mergedClaims.put(AuthConstants.CLAIM_USER_ID, userContext.getUserId());
        }
        if (userContext.getDisplayName() != null) {
            mergedClaims.put(AuthConstants.CLAIM_DISPLAY_NAME, userContext.getDisplayName());
        }
        if (userContext.getPermissions() != null && !userContext.getPermissions().isEmpty()) {
            mergedClaims.put(AuthConstants.CLAIM_PERMISSIONS, userContext.getPermissions());
        }
        return generateToken(userContext.getUsername(), userContext.getRole(), userContext.getFacilityCode(), mergedClaims);
    }

    public static boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            LOGGER.warn("Token 已过期: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            LOGGER.warn("Token 签名无效: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            LOGGER.warn("Token 格式错误: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            LOGGER.warn("Token 不支持: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Token 参数非法: {}", e.getMessage());
            return false;
        } catch (Exception exception) {
            LOGGER.error("验证 Token 失败: {}", exception.getMessage(), exception);
            return false;
        }
    }

    public static Claims getClaimsFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            LOGGER.warn("Token 已过期，仍返回 Claims 供业务读取: {}", e.getMessage());
            return e.getClaims();
        } catch (SignatureException e) {
            LOGGER.warn("Token 签名验证失败: {}", e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            LOGGER.warn("Token 类型不受支持: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Token 参数非法: {}", e.getMessage());
            return null;
        } catch (Exception exception) {
            LOGGER.error("解析 Token 失败: {}", exception.getMessage(), exception);
            return null;
        }
    }

    public static String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        String subject = claims.getSubject();
        if (subject != null && subject.startsWith(AuthConstants.JWT_SUBJECT_PREFIX + ":")) {
            return subject.substring((AuthConstants.JWT_SUBJECT_PREFIX + ":").length());
        }
        return subject;
    }

    public static String getRoleFromToken(String token) {
        return getClaimAsString(token, AuthConstants.CLAIM_ROLE);
    }

    public static String getFacilityCodeFromToken(String token) {
        return getClaimAsString(token, AuthConstants.CLAIM_FACILITY_CODE);
    }

    public static Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get(AuthConstants.CLAIM_USER_ID);
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                LOGGER.warn("无法解析用户ID: {}", text);
            }
        }
        return null;
    }

    public static JwtUserContext getUserContextFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }

        JwtUserContext context = new JwtUserContext();
        context.setUserId(getUserIdFromToken(token));
        context.setUsername(getUsernameFromToken(token));
        context.setRole(getRoleFromToken(token));
        context.setFacilityCode(getFacilityCodeFromToken(token));
        context.setDisplayName(getClaimAsString(token, AuthConstants.CLAIM_DISPLAY_NAME));

        Object permissions = claims.get(AuthConstants.CLAIM_PERMISSIONS);
        if (permissions instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            context.setPermissions(values);
        }
        return context;
    }

    public static Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.getExpiration();
    }

    public static boolean isTokenExpiringWithin24Hours(String token) {
        Date expirationDate = getExpirationDateFromToken(token);
        if (expirationDate == null) {
            return false;
        }
        long timeUntilExpiration = expirationDate.getTime() - System.currentTimeMillis();
        return timeUntilExpiration > 0 && timeUntilExpiration <= 24 * 60 * 60 * 1000L;
    }

    public static long getExpirationTime() {
        return EXPIRATION_TIME;
    }

    public static String getIssuer() {
        return AuthConstants.JWT_ISSUER;
    }

    public static String getSignatureAlgorithm() {
        return SignatureAlgorithm.HS256.getValue();
    }

    private static String getClaimAsString(String token, String claimName) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Object value = claims.get(claimName);
        return value == null ? null : String.valueOf(value);
    }
}