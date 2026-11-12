package com.liana.post.postage.tariff;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class QuoteModels {
    private QuoteModels() { }
    public record Request(@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,64}") String product,
                          @NotBlank @Pattern(regexp = "DOMESTIC|INTERNATIONAL") String scope,
                          @NotBlank @Pattern(regexp = "AIR|SAL|SEA") String mode,
                          @NotBlank @Pattern(regexp = "[A-Z]{2}") String destinationCountry,
                          @Min(1) @Max(1000000) int weightGrams, List<String> additionalServices) {
        public Request {
            additionalServices = additionalServices == null ? List.of() : List.copyOf(additionalServices);
        }
        public Request(String product, String scope, String mode, String destinationCountry, int weightGrams) {
            this(product, scope, mode, destinationCountry, weightGrams, List.of());
        }
    }
    public record Calculation(String ruleId, String algorithm, String zone, int additionalUnits,
                              Integer tierUpToGrams, BigDecimal baseAmount,
                              List<TariffDefinition.Fee> fees, BigDecimal totalAmount) { }
    public record Snapshot(String quoteId, String tariffVersion, String currency, Request input,
                           Calculation calculation, Instant createdAt, Instant expiresAt,
                           String createdBy) { }
    public record View(Snapshot snapshot, boolean valid) { }
}
