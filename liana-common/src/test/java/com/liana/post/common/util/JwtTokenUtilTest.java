package com.liana.post.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token 工具类测试。
 */
@DisplayName("JwtTokenUtil 测试")
public class JwtTokenUtilTest {

    private static String validToken;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_ROLE = "ADMIN";

    @BeforeAll
    public static void setUp() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "test@example.com");
        claims.put("department", "IT");
        claims.put("facility_code", "B1");
        validToken = JwtTokenUtil.generateToken(TEST_USERNAME, TEST_ROLE, "B1", claims);
    }

    @Test
    public void testGenerateToken() {
        String token = JwtTokenUtil.generateToken("user123", "USER", "C2");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));
        assertTrue(JwtTokenUtil.validateToken(token));
    }

    @Test
    public void testGenerateTokenWithAdditionalClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");
        claims.put("phoneNumber", "13800138000");
        claims.put("departmentId", 123);

        String token = JwtTokenUtil.generateToken("user456", "MANAGER", "C1", claims);

        assertTrue(JwtTokenUtil.validateToken(token));

        Claims parsedClaims = JwtTokenUtil.getClaimsFromToken(token);
        assertNotNull(parsedClaims);
        assertEquals("user@example.com", parsedClaims.get("email", String.class));
        assertEquals("13800138000", parsedClaims.get("phoneNumber", String.class));
        assertEquals(123, parsedClaims.get("departmentId", Integer.class));
    }

    @Test
    public void testValidateValidToken() {
        assertTrue(JwtTokenUtil.validateToken(validToken));
    }

    @Test
    public void testValidateInvalidToken() {
        assertFalse(JwtTokenUtil.validateToken("invalid.token.here"));
    }

    @Test
    public void testValidateNullToken() {
        assertFalse(JwtTokenUtil.validateToken(null));
    }

    @Test
    public void testValidateEmptyToken() {
        assertFalse(JwtTokenUtil.validateToken(""));
    }

    @Test
    public void testValidateTamperedToken() {
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + "X";
        assertFalse(JwtTokenUtil.validateToken(tamperedToken));
    }

    @Test
    public void testGetClaimsFromToken() {
        Claims claims = JwtTokenUtil.getClaimsFromToken(validToken);

        assertNotNull(claims);
        assertNotNull(claims.getSubject());
        assertEquals(TEST_ROLE, claims.get("role", String.class));
        assertEquals("B1", claims.get("facility_code", String.class));
        assertEquals("test@example.com", claims.get("email", String.class));
    }

    @Test
    public void testGetFacilityAndContextFromToken() {
        var context = JwtTokenUtil.getUserContextFromToken(validToken);
        assertNotNull(context);
        assertEquals(TEST_USERNAME, context.getUsername());
        assertEquals("ADMIN", context.getRole());
        assertEquals("B1", context.getFacilityCode());
    }

    @Test
    public void testGetExpirationDateFromToken() {
        Date expirationDate = JwtTokenUtil.getExpirationDateFromToken(validToken);
        assertNotNull(expirationDate);
        assertTrue(expirationDate.after(new Date()));
    }

    @Test
    public void testGetUserContextFromPermissions() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("permissions", List.of("MAIL_CREATE", "MAIL_SCAN"));
        String token = JwtTokenUtil.generateToken("user789", "USER", "A1", claims);

        var context = JwtTokenUtil.getUserContextFromToken(token);
        assertNotNull(context);
        assertNotNull(context.getPermissions());
        assertEquals(2, context.getPermissions().size());
        assertTrue(context.getPermissions().contains("MAIL_CREATE"));
    }

    @Test
    public void testGetRoleFromInvalidToken() {
        assertNull(JwtTokenUtil.getRoleFromToken("invalid.token"));
    }

    @Test
    public void testGenerateMultipleDifferentTokens() {
        String token1 = JwtTokenUtil.generateToken("user1", "ADMIN", "B1");
        String token2 = JwtTokenUtil.generateToken("user2", "USER", "B1");
        String token3 = JwtTokenUtil.generateToken("user3", "VIEWER", "C1");

        assertTrue(JwtTokenUtil.validateToken(token1));
        assertTrue(JwtTokenUtil.validateToken(token2));
        assertTrue(JwtTokenUtil.validateToken(token3));

        assertNotEquals(token1, token2);
        assertNotEquals(token2, token3);
        assertNotEquals(token1, token3);

        assertEquals("user1", JwtTokenUtil.getUsernameFromToken(token1));
    }
}