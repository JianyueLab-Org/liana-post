package com.liana.post.oms.model.dto;

import jakarta.validation.constraints.NotBlank;

public class MailLookupRequest {
    @NotBlank
    private String barcode;

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
