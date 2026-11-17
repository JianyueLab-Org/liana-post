package com.liana.post.common.dto.sorting;

import com.liana.post.common.exception.BusinessException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Only scanned, expected mail may be detached. Missing and surplus mail never enter this command. */
public record UnpackingConfirmation(String eventNo, String manifestNo, String bagNo, String facilityCode,
        String actorId, Instant occurredAt, List<String> mailNos) {
    public record Ack(UnpackingConfirmation command, Instant appliedAt) { }

    public UnpackingConfirmation normalized() {
        if (occurredAt == null || mailNos == null || mailNos.size() > 10000) {
            throw new BusinessException(400, "unpacking time and scanned mail required");
        }
        var seen = new HashSet<String>();
        var items = mailNos.stream().map(value -> code(value, 32)).sorted().toList();
        for (String item : items) if (!seen.add(item)) throw new BusinessException(400, "duplicate unpacking mail");
        return new UnpackingConfirmation(code(eventNo, 64), code(manifestNo, 64), code(bagNo, 32),
                code(facilityCode, 64), code(actorId, 64), occurredAt, items);
    }

    public static String code(String value, int max) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1," + max + "}")) {
            throw new BusinessException(400, "invalid unpacking code");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
