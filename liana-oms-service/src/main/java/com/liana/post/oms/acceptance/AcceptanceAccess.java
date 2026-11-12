package com.liana.post.oms.acceptance;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Component
public class AcceptanceAccess {
    public JwtUserContext user(String auth) {
        if (auth==null || !auth.regionMatches(true,0,"Bearer ",0,7) || !JwtTokenUtil.validateToken(auth.substring(7).trim()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录");
        var user=JwtTokenUtil.getUserContextFromToken(auth.substring(7).trim());
        if (user==null || user.getUsername()==null || user.getUsername().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录身份无效");
        if (!("ADMIN".equals(user.getRole()) || (Set.of("CLERK","MANAGER").contains(user.getRole()==null?"":user.getRole())
                && user.getPermissions()!=null && user.getPermissions().contains("MAIL_CREATE")))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无收寄权限");
        return user;
    }
}
