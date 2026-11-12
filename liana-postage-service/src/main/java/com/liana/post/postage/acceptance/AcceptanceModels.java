package com.liana.post.postage.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.liana.post.postage.retail.RetailModels;
import com.liana.post.postage.tariff.QuoteModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AcceptanceModels {
    private AcceptanceModels() { }
    public record Traditional(@NotNull @DecimalMin("0.01") @Digits(integer=6,fraction=2) BigDecimal faceValue,
                              @Min(1) @Max(1000) int quantity) { }
    public record Request(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestId,
                          @NotBlank @Size(max=36) String quoteId, @NotNull JsonNode mail,
                          @NotNull @Size(max=100) List<@NotBlank @Size(max=100) String> stampCodes,
                          @NotNull @Size(max=50) List<@NotNull @Valid Traditional> traditionalStamps,
                          boolean traditionalConfirmed) { }
    public record View(String acceptanceId, String status, String createdBy, String facilityCode,
                       JsonNode mail, QuoteModels.Snapshot quote, List<Traditional> traditionalStamps,
                       List<RetailModels.Stamp> stamps, BigDecimal traditionalAmount, BigDecimal digitalAmount,
                       BigDecimal appliedAmount, BigDecimal excessAmount, String waybillNo,
                       Instant createdAt, Instant completedAt) { }
    public record Summary(String acceptanceId, String status, String waybillNo, QuoteModels.Snapshot quote,
                          List<Traditional> traditionalStamps, BigDecimal traditionalAmount,
                          BigDecimal digitalAmount, BigDecimal appliedAmount, BigDecimal excessAmount,
                          String createdBy, String facilityCode, Instant completedAt) { }
}
