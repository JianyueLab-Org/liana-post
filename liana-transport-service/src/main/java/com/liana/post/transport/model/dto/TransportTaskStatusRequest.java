package com.liana.post.transport.model.dto;

import jakarta.validation.constraints.NotBlank;

public class TransportTaskStatusRequest {
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    @NotBlank
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
