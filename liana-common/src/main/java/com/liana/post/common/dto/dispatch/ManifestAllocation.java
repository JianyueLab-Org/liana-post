package com.liana.post.common.dto.dispatch;

import com.liana.post.common.exception.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Stable service contract for assigning/releasing a whole sealed handoff manifest. Weights are grams. */
public final class ManifestAllocation {
    private ManifestAllocation() { }

    public record Bag(Long dispatchBagId, String bagNo, Integer weightGrams, List<String> mailNos) { }
    public record Snapshot(String manifestNo, int versionNo, String contentHash, String originFacilityCode,
                           String destinationFacilityCode, String facilityRouteCode, List<Bag> bags) { }
    public record Command(String requestNo, String assignmentNo, String manifestNo, int versionNo, String contentHash,
                          String taskCode, String action, String actorId, String reason) {
        public Command normalized() {
            if (versionNo < 1 || contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
                throw new BusinessException(400, "allocation requires a sealed version and content hash");
            }
            String verb = code(action);
            if (!List.of("ASSIGN", "RELEASE").contains(verb)) throw new BusinessException(400, "invalid allocation action");
            if (actorId == null || actorId.isBlank() || actorId.trim().length() > 64) throw new BusinessException(400, "allocation actor is required");
            String note = reason == null ? "" : reason.trim();
            if (note.length() > 512 || ("RELEASE".equals(verb) && note.isEmpty())) throw new BusinessException(400, "release reason is required (max 512)");
            return new Command(code(requestNo), code(assignmentNo), code(manifestNo), versionNo, contentHash,
                    code(taskCode), verb, actorId.trim(), note);
        }
    }
    public record Receipt(Command command, Instant confirmedAt) { }

    public static String code(String value) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1,64}")) throw new BusinessException(400, "invalid allocation identifier");
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
