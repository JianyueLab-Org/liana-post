package com.liana.post.postage.security;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Component
public class PostageAccess {
    public JwtUserContext user(String auth, boolean admin) {
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) unauthorized();
        String token = auth.substring(7).trim();
        if (!JwtTokenUtil.validateToken(token)) unauthorized();
        var user = JwtTokenUtil.getUserContextFromToken(token);
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) unauthorized();
        boolean administrator = "ADMIN".equals(user.getRole());
        boolean clerk = Set.of("CLERK", "MANAGER").contains(user.getRole() == null ? "" : user.getRole());
        boolean mailPermission = user.getPermissions() != null && user.getPermissions().contains("MAIL_CREATE");
        if (admin ? !administrator : !(administrator || (clerk && mailPermission))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无邮资操作权限");
        }
        return user;
    }

    private void unauthorized() { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请提供有效登录令牌"); }
}
