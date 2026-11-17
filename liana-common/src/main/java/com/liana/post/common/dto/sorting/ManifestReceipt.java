package com.liana.post.common.dto.sorting;

import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;

import java.time.Instant;
import java.util.List;

/** A bag receipt is a physical handoff fact, independent of mail unpacking. */
public final class ManifestReceipt {
    private ManifestReceipt() { }
    public record Arrival(String assignmentNo, String taskCode, String manifestNo, int versionNo, String contentHash,
                          String sourceOrgCode, String destinationOrgCode, List<String> bagNos) { }
    public record Event(String eventNo, String requestNo, String manifestNo, int versionNo, String contentHash,
                        String assignmentNo, String taskCode, String bagNo, String sourceOrgCode,
                        String destinationOrgCode, String actorId, Instant receivedAt) {
        public Event normalized() {
            if (versionNo < 1 || contentHash == null || !contentHash.matches("[0-9a-f]{64}") || receivedAt == null
                    || actorId == null || actorId.isBlank() || actorId.length() > 64) throw new BusinessException(400, "invalid bag receipt");
            return new Event(code(eventNo), code(requestNo), code(manifestNo), versionNo, contentHash, code(assignmentNo),
                    code(taskCode), code(bagNo), code(sourceOrgCode), code(destinationOrgCode), actorId, receivedAt);
        }
    }
    public record Ack(Event event, Instant confirmedAt) { }
    private static String code(String value) { return ManifestAllocation.code(value); }
}
