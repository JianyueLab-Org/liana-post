package com.liana.post.sorting.controller;

import com.liana.post.common.model.Result;
import com.liana.post.sorting.service.RepackingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sorting/repack-requests")
public class RepackingController {
    private final RepackingService service;
    public RepackingController(RepackingService service) { this.service = service; }
    @GetMapping("/{requestNo}")
    public Result<RepackingService.Receipt> receipt(@PathVariable("requestNo") String no) { return Result.ok(service.receipt(no)); }
}
