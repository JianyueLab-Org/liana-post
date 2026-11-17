package com.liana.post.common.dto.dispatch;

import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** New bag identity plus immutable links back to actual opening scans. */
public record RepackingCommand(String requestNo, String bagNo, String facilityCode, String destinationCode,
        String actorId, Instant occurredAt, List<Item> items) {
    public record Item(String mailNo, String sourceBagNo, String unpackEventNo, Integer weightGrams) { }
    public record Ack(RepackingCommand command, Instant preparedAt) { }
    public RepackingCommand normalized() {
        if (occurredAt == null || items == null || items.isEmpty() || items.size() > 10000) throw new BusinessException(400, "repacking requires scanned items");
        var seen = new HashSet<String>();
        var normalized = items.stream().map(item -> {
            if (item == null) throw new BusinessException(400, "null repacking item");
            String no = code(item.mailNo(), 32);
            if (!seen.add(no) || (item.weightGrams() != null && item.weightGrams() < 0)) throw new BusinessException(400, "invalid repacking items");
            return new Item(no, code(item.sourceBagNo(), 32), code(item.unpackEventNo(), 64), item.weightGrams());
        }).sorted(Comparator.comparing(Item::mailNo)).toList();
        return new RepackingCommand(code(requestNo, 64), code(bagNo, 32), code(facilityCode, 64), code(destinationCode, 64), code(actorId, 64), occurredAt, normalized);
    }
    private static String code(String value, int max) { return UnpackingConfirmation.code(value, max); }
}
