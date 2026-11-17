package com.liana.post.sorting.controller;

import com.liana.post.common.model.Result;
import com.liana.post.sorting.service.BagForwardingService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sorting/forwarding")
public class BagForwardingController {
    private final BagForwardingService service;
    public BagForwardingController(BagForwardingService service) { this.service = service; }
    @PostMapping
    public Result<BagForwardingService.State> prepare(@RequestBody BagForwardingService.Request request) {
        return Result.ok(service.prepare(request));
    }
    @GetMapping
    public Result<List<BagForwardingService.State>> list(@RequestParam("facilityCode") String facility) {
        return Result.ok(service.list(facility));
    }
    @GetMapping("/candidates")
    public Result<List<BagForwardingService.Candidate>> candidates(@RequestParam("facilityCode") String facility) {
        return Result.ok(service.candidates(facility));
    }
}
