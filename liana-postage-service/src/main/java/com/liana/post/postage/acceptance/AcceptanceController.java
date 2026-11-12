package com.liana.post.postage.acceptance;

import com.liana.post.common.model.Result;
import com.liana.post.postage.security.PostageAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.liana.post.postage.acceptance.AcceptanceModels.*;

@RestController
@RequestMapping("/api/postage/acceptances")
public class AcceptanceController {
    private final AcceptanceStore store;
    private final AcceptanceCoordinator coordinator;
    private final PostageAccess access;
    public AcceptanceController(AcceptanceStore store, AcceptanceCoordinator coordinator, PostageAccess access) {
        this.store=store; this.coordinator=coordinator; this.access=access;
    }
    @PostMapping
    public Result<View> accept(@Valid @RequestBody Request body,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(coordinator.accept(body,access.user(auth,false),auth));
    }
    @GetMapping
    public Result<List<View>> list(@RequestParam(name="page",defaultValue="0") int page,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(store.list(access.user(auth,false),page));
    }
    @GetMapping("/{id}")
    public Result<View> get(@PathVariable("id") String id,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(store.get(id,access.user(auth,false)));
    }
    @GetMapping("/by-waybill/{waybill}")
    public Result<Summary> byWaybill(@PathVariable("waybill") String waybill,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(store.byWaybill(waybill,access.user(auth,false)));
    }
    @GetMapping("/by-request/{requestId}")
    public Result<View> byRequest(@PathVariable("requestId") String requestId,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(store.byRequest(requestId,access.user(auth,false)));
    }
    @PostMapping("/{id}/resume")
    public Result<View> resume(@PathVariable("id") String id,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(coordinator.resume(id,access.user(auth,false),auth));
    }
    @PostMapping("/{id}/cancel")
    public Result<View> cancel(@PathVariable("id") String id,@RequestHeader(value="Authorization",required=false) String auth) {
        return Result.ok(coordinator.cancel(id,access.user(auth,false),auth));
    }
}
