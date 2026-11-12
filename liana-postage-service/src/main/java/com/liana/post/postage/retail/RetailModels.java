package com.liana.post.postage.retail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RetailModels {
    private RetailModels() { }
    public record Line(@NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal faceValue,
                       @Min(1) @Max(100) int quantity) { }
    public record Create(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId,
                         @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> lines) { }
    public record Confirm(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId,
                          @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amountReceived) { }
    public record VoidRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId,
                              @NotBlank @Size(max = 500) String reason) { }
    public record Policy(int revision, String currency, BigDecimal minFaceValue, BigDecimal maxFaceValue,
                         int validityDays, String updatedBy, Instant updatedAt) { }
    public record UpdatePolicy(@Min(1) int revision,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal minFaceValue,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal maxFaceValue,
                               @Min(1) @Max(36500) int validityDays) { }
    public record Sale(String saleId, String requestId, String status, String currency, BigDecimal totalAmount,
                       List<Line> lines, int policyRevision, int validityDays, String createdBy,
                       String facilityCode, Instant createdAt, Instant paidAt) { }
    public record Receipt(String receiptId, String requestId, String method, BigDecimal amountReceived,
                          String confirmedBy, String facilityCode, Instant confirmedAt) { }
    public record Stamp(String stampId, String code, String saleId, String currency, BigDecimal faceValue,
                        String status, String displayStatus, Instant issuedAt, Instant expiresAt,
                        String voidedBy, Instant voidedAt, String voidReason) { }
    public record Audit(String eventId, String subjectType, String subjectId, String eventType,
                        String actor, String facilityCode, Instant occurredAt, String detail) { }
    public record Detail(Sale sale, Receipt receipt, List<Stamp> stamps, List<Audit> audit) { }
}
