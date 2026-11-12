package com.liana.post.postage.tariff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TariffCalculatorTest {
    private TariffDefinition definition;
    private final TariffCalculator calculator = new TariffCalculator();
    private final TariffValidator validator = new TariffValidator();
    @BeforeEach
    void load() throws Exception {
        try (var input = getClass().getResourceAsStream("/initial-tariff.json")) {
            definition = new ObjectMapper().readValue(input, TariffDefinition.class);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "R,DOMESTIC,AIR,LN,20,4.50", "R,DOMESTIC,AIR,LN,21,5.80",
        "R,DOMESTIC,SAL,LN,50,5.80", "R,DOMESTIC,SEA,LN,51,7.00",
        "R,DOMESTIC,AIR,LN,100,7.00", "R,DOMESTIC,AIR,LN,101,11.50",
        "R,DOMESTIC,AIR,LN,250,11.50", "R,DOMESTIC,AIR,LN,251,19.70",
        "R,DOMESTIC,AIR,LN,500,19.70", "R,DOMESTIC,AIR,LN,501,34.70",
        "R,DOMESTIC,AIR,LN,1000,34.70", "R,DOMESTIC,AIR,LN,1001,58.80",
        "R,DOMESTIC,AIR,LN,2000,58.80", "C,DOMESTIC,AIR,LN,1,8.00",
        "C,DOMESTIC,AIR,LN,1000,8.00", "C,DOMESTIC,AIR,LN,1001,11.00",
        "C,DOMESTIC,SAL,LN,20000,65.00", "E,DOMESTIC,AIR,LN,500,18.00",
        "E,DOMESTIC,AIR,LN,501,23.00", "E,DOMESTIC,SEA,LN,20000,213.00",
        "R,INTERNATIONAL,AIR,JP,31,23.00", "R,INTERNATIONAL,AIR,US,21,23.80",
        "R,INTERNATIONAL,SEA,BR,20,20.50", "R,INTERNATIONAL,SAL,HK,2000,119.50",
        "C,INTERNATIONAL,SAL,US,2001,86.00", "C,INTERNATIONAL,SEA,DE,1000,35.00",
        "C,INTERNATIONAL,AIR,AU,30000,567.00", "E,INTERNATIONAL,AIR,JP,1001,129.00",
        "E,INTERNATIONAL,AIR,US,20000,1202.00", "E,INTERNATIONAL,AIR,BR,500,140.00",
        "C,INTERNATIONAL,AIR,ZZ,1000,75.00"
    })
    void approvedPrices(String product, String scope, String mode, String country, int grams, String price) {
        var result = calculator.calculate(definition, new QuoteModels.Request(product, scope, mode, country, grams));
        assertEquals(new BigDecimal(price), result.totalAmount());
    }

    @Test
    void allConfiguredServicesAcceptLimitsAndRejectOneGramOver() {
        validator.validate(definition);
        assertEquals(30, definition.rules().size());
        for (var rule : definition.rules()) {
            String country = switch (rule.zone()) { case "DOM" -> "LN"; case "Z1" -> "JP"; case "Z2" -> "US"; default -> "BR"; };
            var min = calculator.calculate(definition, new QuoteModels.Request(rule.product(), rule.scope(), rule.mode(), country, 1));
            var max = calculator.calculate(definition, new QuoteModels.Request(rule.product(), rule.scope(), rule.mode(), country, rule.maxWeightGrams()));
            assertTrue(max.totalAmount().compareTo(min.totalAmount()) >= 0);
            assertThrows(IllegalArgumentException.class, () -> calculator.calculate(definition,
                    new QuoteModels.Request(rule.product(), rule.scope(), rule.mode(), country, rule.maxWeightGrams() + 1)));
        }
    }

    @Test
    void rejectsScopeMismatchUnsupportedModeAndInvalidWeight() {
        for (var q : List.of(new QuoteModels.Request("E", "INTERNATIONAL", "SEA", "JP", 500),
                new QuoteModels.Request("C", "DOMESTIC", "AIR", "US", 1000),
                new QuoteModels.Request("C", "INTERNATIONAL", "AIR", "LN", 1000),
                new QuoteModels.Request("C", "DOMESTIC", "AIR", "LN", 0))) {
            assertThrows(IllegalArgumentException.class, () -> calculator.calculate(definition, q));
        }
    }

    @Test
    void rejectsDuplicateRulesMissingZoneAndFractionalMoney() {
        var duplicate = new ArrayList<>(definition.rules());
        duplicate.add(duplicate.get(0));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(withRules(duplicate)));
        var incomplete = new ArrayList<>(definition.rules());
        incomplete.removeIf(r -> r.id().equals("I_U_AIR_Z1"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(withRules(incomplete)));
        var original = definition.rules().get(1);
        var badMoney = new TariffDefinition.Rule(original.id(), original.product(), original.scope(), original.mode(), original.zone(),
                original.algorithm(), original.maxWeightGrams(), original.firstWeightGrams(), original.stepWeightGrams(),
                new BigDecimal("8.001"), original.stepPrice(), original.tiers(), original.fees());
        var changed = new ArrayList<>(definition.rules());
        changed.set(1, badMoney);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(withRules(changed)));
    }

    @Test
    void rejectsUncoveredOrDecreasingWeightTiers() {
        var r = definition.rules().get(0);
        for (var tiers : List.of(List.of(new TariffDefinition.Tier(1999, BigDecimal.ONE)),
                List.of(new TariffDefinition.Tier(20, BigDecimal.TEN), new TariffDefinition.Tier(2000, BigDecimal.ONE)))) {
            var changed = new ArrayList<>(definition.rules());
            changed.set(0, new TariffDefinition.Rule(r.id(), r.product(), r.scope(), r.mode(), r.zone(), r.algorithm(),
                    r.maxWeightGrams(), null, null, null, null, tiers, r.fees()));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(withRules(changed)));
        }
    }

    @Test
    void ordinaryAndRegisteredLettersUseSameBaseWithOptionalFee() {
        var ordinary = calculator.calculate(definition, new QuoteModels.Request("U", "DOMESTIC", "AIR", "LN", 20));
        var registered = calculator.calculate(definition, new QuoteModels.Request("U", "DOMESTIC", "AIR", "LN", 20, List.of("REGISTRATION")));
        assertEquals(new BigDecimal("1.50"), ordinary.totalAmount());
        assertTrue(ordinary.fees().isEmpty());
        assertEquals(new BigDecimal("4.50"), registered.totalAmount());
        assertEquals(ordinary.baseAmount(), registered.baseAmount());
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(definition,
                new QuoteModels.Request("U", "DOMESTIC", "AIR", "LN", 20, List.of("REGISTRATION", "REGISTRATION"))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(definition,
                new QuoteModels.Request("C", "DOMESTIC", "AIR", "LN", 20, List.of("REGISTRATION"))));
    }

    private TariffDefinition withRules(List<TariffDefinition.Rule> rules) {
        return new TariffDefinition(definition.currency(), definition.domesticCountry(), definition.fallbackZone(),
                definition.countryZones(), definition.products(), rules);
    }

    @Test
    void legacyPublishedRDefinitionStillQuotesBothLetterOptions() {
        var json = new ObjectMapper();
        var products = definition.products().stream().map(p -> "U".equals(p.code()) ? new TariffDefinition.Product("R", p.name()) : p).toList();
        var rules = definition.rules().stream().map(r -> new TariffDefinition.Rule(r.id(), "U".equals(r.product()) ? "R" : r.product(), r.scope(), r.mode(), r.zone(), r.algorithm(), r.maxWeightGrams(), r.firstWeightGrams(), r.stepWeightGrams(), r.firstPrice(), r.stepPrice(), r.tiers(), r.fees().stream().map(f -> new TariffDefinition.Fee(f.code(), f.amount())).toList())).toList();
        var legacy = new TariffDefinition(definition.currency(), definition.domesticCountry(), definition.fallbackZone(), definition.countryZones(), products, rules);
        validator.validate(legacy);
        assertEquals(new BigDecimal("7.00"), calculator.calculate(legacy, new QuoteModels.Request("U", "INTERNATIONAL", "AIR", "JP", 31)).totalAmount());
        assertEquals(new BigDecimal("23.00"), calculator.calculate(legacy, new QuoteModels.Request("U", "INTERNATIONAL", "AIR", "JP", 31, List.of("REGISTRATION"))).totalAmount());
        assertEquals(new BigDecimal("23.00"), calculator.calculate(legacy, new QuoteModels.Request("R", "INTERNATIONAL", "AIR", "JP", 31)).totalAmount());
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(legacy, new QuoteModels.Request("U", "DOMESTIC", "AIR", "LN", 20, List.of("INSURANCE"))));
    }
}
