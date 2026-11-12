package com.liana.post.postage.tariff;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Immutable, versioned configuration; money uses decimal values, weight uses grams. */
public record TariffDefinition(String currency, String domesticCountry, String fallbackZone,
                               Map<String, String> countryZones, List<Product> products,
                               List<Rule> rules) {
    public record Product(String code, String name) { }
    public record Tier(int upToGrams, BigDecimal price) { }
    public record Fee(String code, BigDecimal amount, boolean optional) {
        public Fee(String code, BigDecimal amount) { this(code, amount, false); }
    }
    public record Rule(String id, String product, String scope, String mode, String zone,
                       String algorithm, int maxWeightGrams, Integer firstWeightGrams,
                       Integer stepWeightGrams, BigDecimal firstPrice, BigDecimal stepPrice,
                       List<Tier> tiers, List<Fee> fees) { }
}
