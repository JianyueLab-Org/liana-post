package com.liana.post.postage.retail;

import com.liana.post.common.model.Result;
import com.liana.post.postage.security.PostageAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.liana.post.postage.retail.RetailModels.*;

@RestController
@RequestMapping("/api/postage")
public class RetailController {
    public record Lookup(@NotBlank @Size(max = 100) String code) { }
    private final RetailService service;
    private final PostageAccess access;
    public RetailController(RetailService service, PostageAccess access) { this.service = service; this.access = access; }

    @GetMapping("/stamp-policy")
    public Result<Policy> policy(@RequestHeader(value = "Authorization", required = false) String auth) {
        access.user(auth, false);
        return Result.ok(service.policy());
    }
    @PutMapping("/stamp-policy")
    public Result<Policy> policy(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody UpdatePolicy body) {
        return Result.ok(service.updatePolicy(body, access.user(auth, true)));
    }
    @GetMapping("/stamp-policy/audit")
    public Result<List<Audit>> policyAudit(@RequestHeader(value = "Authorization", required = false) String auth) {
        return Result.ok(service.policyAudit(access.user(auth, true)));
    }
    @PostMapping("/sales")
    public Result<Detail> create(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody Create body) {
        return Result.ok(service.create(body, access.user(auth, false)));
    }
    @GetMapping("/sales")
    public Result<List<Sale>> sales(@RequestHeader(value = "Authorization", required = false) String auth,
                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size) {
        return Result.ok(service.sales(access.user(auth, false), page, size));
    }
    @GetMapping("/sales/{id}")
    public Result<Detail> detail(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id) {
        return Result.ok(service.detail(id, access.user(auth, false)));
    }
    @PostMapping("/sales/{id}/confirm-cash")
    public Result<Detail> confirm(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id, @Valid @RequestBody Confirm body) {
        return Result.ok(service.confirm(id, body, access.user(auth, false)));
    }
    @PostMapping("/sales/{id}/cancel")
    public Result<Detail> cancel(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id) {
        return Result.ok(service.cancel(id, access.user(auth, false)));
    }
    @PostMapping("/stamps/lookup")
    public Result<Stamp> lookup(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody Lookup body) {
        access.user(auth, false);
        return Result.ok(service.lookup(body.code()));
    }
    @PostMapping("/stamps/{id}/void")
    public Result<Stamp> voidStamp(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") String id, @Valid @RequestBody VoidRequest body) {
        return Result.ok(service.voidStamp(id, body, access.user(auth, true)));
    }
}
