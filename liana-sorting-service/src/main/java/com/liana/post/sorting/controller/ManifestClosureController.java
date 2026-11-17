package com.liana.post.sorting.controller;

import com.liana.post.common.model.Result;
import com.liana.post.sorting.service.ManifestClosureService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sorting/manifests/{manifestNo}")
public class ManifestClosureController {
    private final ManifestClosureService service;
    public ManifestClosureController(ManifestClosureService service) { this.service = service; }
    @PostMapping("/finish-check")
    public Result<ManifestClosureService.CheckReceipt> finish(@PathVariable("manifestNo") String no, @RequestBody ManifestClosureService.Request request) { return Result.ok(service.finishCheck(no, request)); }
    @PostMapping("/close")
    public Result<ManifestClosureService.State> close(@PathVariable("manifestNo") String no, @RequestBody ManifestClosureService.Request request) { return Result.ok(service.close(no, request)); }
    @GetMapping("/closure")
    public Result<ManifestClosureService.State> state(@PathVariable("manifestNo") String no) { return Result.ok(service.state(no)); }
}
