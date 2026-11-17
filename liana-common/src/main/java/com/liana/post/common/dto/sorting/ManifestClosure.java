package com.liana.post.common.dto.sorting;

import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/** Administrative settlement never creates physical receipt events. */
public final class ManifestClosure {
    private ManifestClosure() { }
    public record Command(String requestNo, ManifestReceipt.Arrival arrival, List<String> receivedBagNos,
                          List<String> missingBagNos, String actorId, Instant closedAt) {
        public Command normalized() {
            if (arrival == null || arrival.versionNo() < 1 || arrival.contentHash() == null
                    || !arrival.contentHash().matches("[0-9a-f]{64}") || actorId == null || actorId.isBlank()
                    || actorId.length() > 64 || closedAt == null) throw new BusinessException(400, "invalid manifest closure");
            var all = bags(arrival.bagNos()); var received = bags(receivedBagNos); var missing = bags(missingBagNos);
            var partition = new TreeSet<>(received); partition.addAll(missing);
            if (all.isEmpty() || received.size() + missing.size() != partition.size() || !partition.equals(new TreeSet<>(all))) {
                throw new BusinessException(400, "closure must partition every expected bag exactly once");
            }
            var a = new ManifestReceipt.Arrival(code(arrival.assignmentNo()), code(arrival.taskCode()), code(arrival.manifestNo()),
                    arrival.versionNo(), arrival.contentHash(), code(arrival.sourceOrgCode()), code(arrival.destinationOrgCode()), all);
            return new Command(code(requestNo), a, received, missing, actorId, closedAt);
        }
    }
    public record Ack(Command command, String destination, Instant confirmedAt) { }
    private static List<String> bags(List<String> input) {
        if (input == null) throw new BusinessException(400, "bag partition required");
        var result = input.stream().map(ManifestClosure::code).sorted().toList();
        if (new HashSet<>(result).size() != result.size()) throw new BusinessException(400, "duplicate closure bag");
        return result;
    }
    private static String code(String value) { return ManifestAllocation.code(value); }
}
