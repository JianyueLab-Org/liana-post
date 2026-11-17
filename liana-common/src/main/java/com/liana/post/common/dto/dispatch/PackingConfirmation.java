package com.liana.post.common.dto.dispatch;

import com.liana.post.common.exception.BusinessException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fixed command identity and complete packing content; weights are grams from OMS, not estimates. */
public record PackingConfirmation(String eventNo, String bagNo, String facilityCode, int packingVersion, List<Item> items) {
    public record Item(String mailNo, Integer weightGrams) { }

    public PackingConfirmation normalized() {
        if (packingVersion != 1 || items == null || items.isEmpty()) {
            throw new BusinessException(400, "packing requires version 1 and nonempty items");
        }
        Set<String> seen = new HashSet<>();
        List<Item> normalizedItems = items.stream().map(item -> {
            if (item == null) throw new BusinessException(400, "null packing item");
            String no = code(item.mailNo(), 32);
            if (!seen.add(no)) throw new BusinessException(400, "duplicate packing item: " + no);
            if (item.weightGrams() != null && item.weightGrams() < 0) {
                throw new BusinessException(400, "negative mail weight");
            }
            return new Item(no, item.weightGrams());
        }).toList();
        return new PackingConfirmation(code(eventNo, 64), code(bagNo, 32), code(facilityCode, 64), 1, normalizedItems);
    }

    private static String code(String value, int max) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1," + max + "}")) {
            throw new BusinessException(400, "invalid packing code");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
