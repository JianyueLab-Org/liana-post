package com.liana.post.transport.model.dto;

import jakarta.validation.constraints.NotBlank;

public class TransportTaskStatusRequest {
    @NotBlank
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
