package com.liana.post.postage.tariff;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TariffCalculator {
    public QuoteModels.Calculation calculate(TariffDefinition d, QuoteModels.Request q) {
        if (q.weightGrams() <= 0 || q.weightGrams() > 1000000) throw new IllegalArgumentException("重量必须为正整数克且不超过 1000000");
        boolean domestic = "DOMESTIC".equals(q.scope());
        if (!domestic && !"INTERNATIONAL".equals(q.scope())) throw new IllegalArgumentException("寄递范围无效");
        if (domestic != d.domesticCountry().equals(q.destinationCountry())) throw new IllegalArgumentException("目的国家与国内/国际范围不一致");
        String zone = domestic ? "DOM" : d.countryZones().getOrDefault(q.destinationCountry(), d.fallbackZone());
        var services = q.additionalServices();
        if (services.size() != new java.util.HashSet<>(services).size()
                || services.stream().anyMatch(s -> !"REGISTRATION".equals(s))
                || (!services.isEmpty() && !java.util.Set.of("U", "R").contains(q.product()))) {
            throw new IllegalArgumentException("附加服务无效或不适用于此产品");
        }
        // Legacy R tariff versions can quote ordinary letters without rewriting published JSON.
        boolean legacyLetter = "U".equals(q.product()) && d.rules().stream().noneMatch(rule -> "U".equals(rule.product()));
        boolean legacyRequest = "R".equals(q.product()) && d.rules().stream().noneMatch(rule -> "R".equals(rule.product()));
        String product = legacyLetter ? "R" : legacyRequest ? "U" : q.product();
        var r = d.rules().stream().filter(rule -> rule.product().equals(product)
                && rule.scope().equals(q.scope()) && rule.mode().equals(q.mode()) && rule.zone().equals(zone))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("此产品/运输方式/分区暂无资费"));
        if (q.weightGrams() > r.maxWeightGrams()) throw new IllegalArgumentException("超出产品重量上限 " + r.maxWeightGrams() + " 克");
        int units = 0;
        Integer tierUpper = null;
        BigDecimal base;
        if ("TIERS".equals(r.algorithm())) {
            var tier = r.tiers().stream().filter(t -> q.weightGrams() <= t.upToGrams()).findFirst().orElseThrow();
            tierUpper = tier.upToGrams();
            base = tier.price();
        } else {
            long excess = Math.max(0L, (long) q.weightGrams() - r.firstWeightGrams());
            units = (int) ((excess + r.stepWeightGrams() - 1L) / r.stepWeightGrams());
            base = r.firstPrice().add(r.stepPrice().multiply(BigDecimal.valueOf(units)));
        }
        var fees = r.fees().stream().filter(f -> {
            if (legacyLetter && "REGISTRATION".equals(f.code())) return services.contains(f.code());
            return !f.optional() || services.contains(f.code()) || ("R".equals(q.product()) && "REGISTRATION".equals(f.code()));
        }).toList();
        if (services.contains("REGISTRATION") && fees.stream().noneMatch(f -> "REGISTRATION".equals(f.code()))) {
            throw new IllegalArgumentException("此规则未配置挂号费");
        }
        BigDecimal total = base;
        for (var f : fees) total = total.add(f.amount());
        return new QuoteModels.Calculation(r.id(), r.algorithm(), zone, units, tierUpper,
                base.setScale(2), fees, total.setScale(2));
    }
}
