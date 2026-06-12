package com.liana.post.dispatch.model.dto;

import jakarta.validation.constraints.NotNull;

public class DispatchBatchApproveRequest {
    @NotNull
    private Long approvedBy;

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
}