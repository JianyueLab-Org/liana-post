package com.liana.post.common.dto.dispatch;

import com.liana.post.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;

/** A sealed container changes its transport leg, never its original packing identity. */
public final class BagForwarding {
    private BagForwarding() { }

    public record Bag(String bagNo, String receiptEventNo) { }
    public record Command(String requestNo, String previousManifestNo, String manifestNo, String batchNo,
                          String sourceOrgCode, String destinationOrgCode, String facilityRouteCode,
                          String actorId, List<Bag> bags) {
        public Command normalized() {
            if (bags == null || bags.isEmpty() || actorId == null || actorId.isBlank() || actorId.length() > 64) {
                throw new BusinessException(400, "forwarding requires received bags and an actor");
            }
            var normalized = bags.stream().map(b -> {
                if (b == null) throw new BusinessException(400, "invalid forwarding bag");
                return new Bag(ManifestAllocation.code(b.bagNo()), ManifestAllocation.code(b.receiptEventNo()));
            }).sorted(java.util.Comparator.comparing(Bag::bagNo)).toList();
            if (normalized.stream().map(Bag::bagNo).distinct().count() != normalized.size()) {
                throw new BusinessException(400, "duplicate forwarding bag");
            }
            String source = ManifestAllocation.code(sourceOrgCode), target = ManifestAllocation.code(destinationOrgCode);
            String previous = ManifestAllocation.code(previousManifestNo), next = ManifestAllocation.code(manifestNo);
            if (ManifestAllocation.code(batchNo).length() > 32 || normalized.stream().anyMatch(b -> b.bagNo().length() > 32)) throw new BusinessException(400, "batch and bag identifiers exceed storage limits");
            if (source.equals(target) || previous.equals(next)) throw new BusinessException(400, "forwarding requires a new leg");
            return new Command(ManifestAllocation.code(requestNo), previous, next, ManifestAllocation.code(batchNo),
                    source, target, ManifestAllocation.code(facilityRouteCode), actorId.trim(), normalized);
        }
    }
    public record Ack(Command command, Instant confirmedAt) { }
}
