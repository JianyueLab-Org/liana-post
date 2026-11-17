package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DispatchBatchCancelRequest(@NotBlank @Size(max = 512) String reason) { }
