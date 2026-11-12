package com.liana.post.postage.tariff;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Component
public class TariffValidator {
    public void validate(TariffDefinition d) {
        require(d != null, "资费定义不能为空");
        require("LNR".equals(d.currency()), "首版币种必须为 LNR");
        require("LN".equals(d.domesticCountry()), "国内国家代码必须为 LN");
        require(code(d.fallbackZone()), "必须指定默认国际分区");
        require(d.countryZones() != null && d.countryZones().size() <= 1000, "国家分区配置无效");
        d.countryZones().forEach((country, zone) -> {
            require(country != null && country.matches("[A-Z]{2}") && !country.equals("LN"), "国际国家代码无效");
            require(code(zone), "分区代码无效");
        });
        require(d.products() != null && !d.products().isEmpty() && d.products().size() <= 100, "产品目录无效");
        Set<String> products = new HashSet<>();
        for (var p : d.products()) {
            require(p != null && code(p.code()) && p.name() != null && !p.name().isBlank()
                    && p.name().length() <= 100 && products.add(p.code()), "产品代码重复或产品无效");
        }
        require(d.rules() != null && !d.rules().isEmpty() && d.rules().size() <= 2000, "资费规则不能为空或过多");
        Set<String> ids = new HashSet<>();
        Set<String> combinations = new HashSet<>();
        Set<String> coveredProducts = new HashSet<>();
        Set<String> zones = new HashSet<>(d.countryZones().values());
        zones.add(d.fallbackZone());
        Set<String> internationalServices = new HashSet<>();
        for (var r : d.rules()) {
            require(r != null && code(r.id()) && ids.add(r.id()), "规则标识无效或重复");
            require(products.contains(r.product()), "规则引用了未知产品");
            coveredProducts.add(r.product());
            require(Set.of("DOMESTIC", "INTERNATIONAL").contains(value(r.scope())), "寄递范围无效");
            require(Set.of("AIR", "SAL", "SEA").contains(value(r.mode())), "运输方式无效");
            require("DOMESTIC".equals(r.scope()) ? "DOM".equals(r.zone()) : zones.contains(r.zone()), "规则分区无效");
            String service = r.product() + "/" + r.scope() + "/" + r.mode();
            require(combinations.add(service + "/" + r.zone()), "同一产品/范围/方式/分区只能有一条规则");
            if ("INTERNATIONAL".equals(r.scope())) internationalServices.add(service);
            require(r.maxWeightGrams() > 0 && r.maxWeightGrams() <= 1000000, "最大重量无效");
            require(r.fees() != null && r.fees().size() <= 100, "附加费列表无效");
            Set<String> feeCodes = new HashSet<>();
            for (var f : r.fees()) {
                require(f != null && code(f.code()) && feeCodes.add(f.code()), "附加费代码无效或重复");
                require(!f.optional() || ("REGISTRATION".equals(f.code()) && Set.of("U", "R").contains(r.product())), "首版可选服务仅支持信函挂号");
                require(!("U".equals(r.product()) && "REGISTRATION".equals(f.code())) || f.optional(), "普通信函挂号费必须设为可选");
                money(f.amount());
            }
            if ("FIRST_STEP".equals(r.algorithm())) {
                require(r.firstWeightGrams() != null && r.firstWeightGrams() > 0
                        && r.firstWeightGrams() <= r.maxWeightGrams()
                        && r.stepWeightGrams() != null && r.stepWeightGrams() > 0, "首重/续重单位无效");
                money(r.firstPrice());
                money(r.stepPrice());
                require(r.tiers() != null && r.tiers().isEmpty(), "首重续重规则不能含重量档");
            } else if ("TIERS".equals(r.algorithm())) {
                require(r.firstWeightGrams() == null && r.stepWeightGrams() == null
                        && r.firstPrice() == null && r.stepPrice() == null, "重量档规则不能含首重续重参数");
                require(r.tiers() != null && !r.tiers().isEmpty() && r.tiers().size() <= 1000, "重量档不能为空或过多");
                int lastWeight = 0;
                BigDecimal lastPrice = BigDecimal.ZERO;
                for (var tier : r.tiers()) {
                    require(tier != null && tier.upToGrams() > lastWeight, "重量档上限必须严格递增");
                    money(tier.price());
                    require(tier.price().compareTo(lastPrice) >= 0, "重量增加不能降价");
                    lastWeight = tier.upToGrams();
                    lastPrice = tier.price();
                }
                require(lastWeight == r.maxWeightGrams(), "重量档必须完整覆盖最大重量");
            } else {
                throw new IllegalArgumentException("不支持的计价算法");
            }
        }
        require(coveredProducts.equals(products), "每个产品至少需要一条资费规则");
        for (String service : internationalServices) {
            for (String zone : zones) require(combinations.contains(service + "/" + zone), "国际服务必须覆盖所有配置分区");
        }
    }

    private boolean code(String v) { return v != null && v.matches("[A-Z0-9_-]{1,64}"); }
    private String value(String v) { return v == null ? "" : v; }
    private void money(BigDecimal v) {
        require(v != null && v.signum() >= 0 && v.compareTo(new BigDecimal("1000000")) <= 0
                && v.stripTrailingZeros().scale() <= 2, "金额必须非负、最多两位小数且不超过 1000000");
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
