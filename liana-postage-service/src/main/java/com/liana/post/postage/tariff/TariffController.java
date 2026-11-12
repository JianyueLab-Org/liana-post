package com.liana.post.postage.tariff;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/postage")
public class TariffController {
    public record CreateDraft(@NotNull @Pattern(regexp = "[A-Z0-9_-]{1,64}") String versionId,
                              @NotNull TariffDefinition definition) { }
    public record UpdateDraft(@Min(1) int revision, @NotNull TariffDefinition definition) { }
    public record Publish(@Min(1) int revision, Instant effectiveFrom) { }
    private final TariffService service;
    public TariffController(TariffService service) { this.service = service; }

    @GetMapping("/tariffs/current")
    public Result<TariffStore.Version> current(@RequestHeader(value = "Authorization", required = false) String auth) {
        user(auth, false);
        return Result.ok(service.current());
    }
    @GetMapping("/tariffs")
    public Result<List<TariffStore.Version>> versions(@RequestHeader(value = "Authorization", required = false) String auth) {
        user(auth, true);
        return Result.ok(service.versions());
    }
    @GetMapping("/tariffs/{id}")
    public Result<TariffStore.Version> version(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id) {
        user(auth, true);
        return Result.ok(service.version(id));
    }
    @PostMapping("/tariffs")
    public Result<TariffStore.Version> create(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody CreateDraft body) {
        var actor = user(auth, true);
        return Result.ok(service.create(body.versionId(), body.definition(), actor.getUsername()));
    }
    @PutMapping("/tariffs/{id}")
    public Result<TariffStore.Version> update(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable("id") String id, @Valid @RequestBody UpdateDraft body) {
        var actor = user(auth, true);
        return Result.ok(service.update(id, body.revision(), body.definition(), actor.getUsername()));
    }
    @PostMapping("/tariffs/{id}/publish")
    public Result<TariffStore.Version> publish(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @PathVariable("id") String id, @Valid @RequestBody Publish body) {
        var actor = user(auth, true);
        return Result.ok(service.publish(id, body.revision(), body.effectiveFrom(), actor.getUsername()));
    }
    @PostMapping("/quotes")
    public Result<QuoteModels.Snapshot> quote(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody QuoteModels.Request body) {
        var actor = user(auth, false);
        return Result.ok(service.quote(body, actor.getUsername()));
    }
    @GetMapping("/quotes/{id}")
    public Result<QuoteModels.View> quote(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id) {
        user(auth, false);
        return Result.ok(service.quote(id));
    }

    // Validate the actual signed token; identity headers alone are never trusted on direct access.
    private JwtUserContext user(String auth, boolean admin) {
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
