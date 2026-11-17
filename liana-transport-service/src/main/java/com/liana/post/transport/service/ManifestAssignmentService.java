package com.liana.post.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.constant.AuthConstants;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.transport.client.DispatchClient;
import com.liana.post.transport.model.dto.TransportTaskRequest;
import com.liana.post.transport.model.dto.TransportTaskStatusRequest;
import com.liana.post.transport.model.entity.*;
import com.liana.post.transport.repository.TransportRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Transactions reserve a complete sealed manifest; HTTP is always outside the local transaction. */
@Service
public class ManifestAssignmentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransportRepository repository;
    private final DispatchClient dispatch;
    private final HttpServletRequest http;
    private final TransactionTemplate tx;
    private final String token;

    public ManifestAssignmentService(JdbcTemplate jdbc, ObjectMapper json, TransportRepository repository,
            DispatchClient dispatch, HttpServletRequest http, PlatformTransactionManager manager,
            @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.repository = repository; this.dispatch = dispatch;
        this.http = http; this.tx = new TransactionTemplate(manager); this.token = token;
    }

    public TransportTaskEntity create(TransportTaskRequest request) {
        var user = user();
        if (request == null) throw new BusinessException(400, "manifest is required");
        var snapshot = load(request.getManifestNo(), request.getDispatchBagId());
        own(user, snapshot.originFacilityCode());
        if (request.getAssetId() != null || request.getRouteId() != null) throw new BusinessException(400, "select a schedule for route and asset");
        try {
            return tx.execute(s -> {
                TransportScheduleEntity schedule = request.getScheduleId() == null ? null : repository.lockSchedule(request.getScheduleId());
                if (schedule != null) {
                    var existing = repository.findActiveTaskByScheduleId(schedule.getId()).orElse(null);
                    if (existing != null) {
                        var locked = lockTask(existing.getTaskCode(), schedule.getId());
                        ownTask(user, locked); requirePending(locked);
                        var all = new ArrayList<>(snapshots(locked.getId())); all.add(snapshot);
                        validateSchedule(schedule, all, locked.getId());
                        reserve(locked, snapshot, actor(user));
                        return enrich(locked);
                    }
                }
                var task = new TransportTaskEntity();
                task.setTaskCode(request.getTaskCode() == null || request.getTaskCode().isBlank() ? "TT-" + id() : ManifestAllocation.code(request.getTaskCode()));
                task.setStatus("CREATED"); task.setCreatedAt(now());
                if (schedule != null) {
                    validateSchedule(schedule, List.of(snapshot), null);
                    task.setScheduleId(schedule.getId()); task.setRouteId(schedule.getRouteId()); task.setAssetId(schedule.getAssetId()); task.setStatus("ASSIGNED");
                }
                task = repository.saveTask(task);
                reserve(task, snapshot, actor(user));
                return enrich(task);
            });
        } catch (DuplicateKeyException e) { throw new BusinessException(409, "manifest, bag or task number is already reserved"); }
    }

    public TransportTaskEntity add(String taskCode, String manifestNo) {
        var user = user();
        var snapshot = load(manifestNo, null);
        own(user, snapshot.originFacilityCode());
        try {
            return tx.execute(s -> {
                var task = lockTask(taskCode, null);
                requirePending(task); ownTask(user, task);
                var previous = snapshots(task.getId());
                for (var old : previous) if (!sameLeg(old, snapshot)) throw new BusinessException(409, "all manifests on a task must share the same route and endpoints");
                var all = new ArrayList<>(previous); all.add(snapshot);
                if (task.getScheduleId() != null) validateSchedule(repository.findScheduleById(task.getScheduleId()).orElseThrow(), all, task.getId());
                reserve(task, snapshot, actor(user));
                return enrich(task);
            });
        } catch (DuplicateKeyException e) { throw new BusinessException(409, "manifest or bag is already reserved"); }
    }

    public TransportTaskEntity assign(String code, Long scheduleId) {
        var user = user();
        if (scheduleId == null) throw new BusinessException(400, "schedule is required");
        return tx.execute(s -> {
            var task = lockTask(code, scheduleId);
            requirePending(task); ownTask(user, task);
            if (task.getScheduleId() != null) throw new BusinessException(409, "cancel the task before changing its schedule");
            if (repository.findActiveTaskByScheduleId(scheduleId).isPresent()) throw new BusinessException(409, "schedule already has a task; add this manifest to that task instead");
            validateSchedule(repository.findScheduleById(scheduleId).orElseThrow(), snapshots(task.getId()), task.getId());
            var schedule = repository.findScheduleById(scheduleId).orElseThrow();
            task.setScheduleId(scheduleId); task.setAssetId(schedule.getAssetId()); task.setRouteId(schedule.getRouteId()); task.setStatus("ASSIGNED");
            return enrich(repository.updateTask(task));
        });
    }

    public TransportTaskEntity status(String code, TransportTaskStatusRequest request) {
        var user = user();
        String target = ManifestAllocation.code(request.getStatus());
        // Verify source facts before taking task/schedule locks, then fence changes by the exact assignment set.
        List<String> checked = List.of();
        if ("DEPARTED".equals(target)) {
            var task = repository.findTaskByCode(code).orElseThrow(() -> new BusinessException(404, "task not found"));
            ownTask(user, task);
            checked = assignmentNos(task.getId());
            for (var snapshot : snapshots(task.getId())) for (var bag : snapshot.bags()) {
                var response = dispatch.getBagById(bag.dispatchBagId());
                var source = response == null || !response.isSuccess() ? null : response.getData();
                if (source == null || !"DISPATCHED".equals(source.getStatus()) || !task.getTaskCode().equals(source.getTransportTaskCode())) {
                    throw new BusinessException(409, "all manifest bags must be handed off to this task before departure");
                }
            }
        }
        var expected = checked;
        return tx.execute(s -> {
            var task = lockTask(code, null); ownTask(user, task);
            if ("CANCELLED".equals(target)) {
                if ("CANCELLED".equals(task.getStatus()) || "CANCELLING".equals(task.getStatus())) return enrich(task);
                requirePending(task);
                if (request.getReason() == null || request.getReason().isBlank()) throw new BusinessException(400, "cancellation reason is required");
                for (var row : jdbc.queryForList("SELECT assignment_no,snapshot_json FROM transport_manifest_assignment WHERE task_id=? ORDER BY assignment_no", task.getId())) {
                    var snap = read((String) row.get("snapshot_json"), ManifestAllocation.Snapshot.class);
                    enqueue(new ManifestAllocation.Command(id(), (String) row.get("assignment_no"), snap.manifestNo(), snap.versionNo(), snap.contentHash(), task.getTaskCode(), "RELEASE", actor(user), request.getReason()).normalized());
                }
                jdbc.update("UPDATE transport_manifest_assignment SET status='RELEASING' WHERE task_id=? AND status<>'RELEASED'", task.getId());
                task.setStatus("CANCELLING");
            } else {
                if (target.equals(task.getStatus())) return enrich(task);
                boolean allowed = switch (task.getStatus()) {
                    case "ASSIGNED" -> "DEPARTED".equals(target);
                    case "DEPARTED" -> List.of("IN_TRANSIT", "ARRIVED").contains(target);
                    case "IN_TRANSIT" -> "ARRIVED".equals(target);
                    case "ARRIVED" -> "COMPLETED".equals(target);
                    default -> false;
                };
                if (!allowed) throw new BusinessException(409, "invalid task status transition");
                if ("COMPLETED".equals(target) && (assignmentNos(task.getId()).isEmpty()
                        || jdbc.queryForObject("SELECT COUNT(*) FROM transport_manifest_assignment WHERE task_id=? AND status NOT IN ('RECEIVED','SETTLED')", Long.class, task.getId()) != 0)) {
                    throw new BusinessException(409, "all manifest assignments must be received or settled before task completion");
                }
                if ("DEPARTED".equals(target)) {
                    if (expected.isEmpty() || !expected.equals(assignmentNos(task.getId())) || jdbc.queryForObject("SELECT COUNT(*) FROM transport_manifest_assignment WHERE task_id=? AND status<>'CONFIRMED'", Long.class, task.getId()) != 0) {
                        throw new BusinessException(409, "all manifest assignments must be confirmed before departure");
                    }
                    validateSchedule(repository.findScheduleById(task.getScheduleId()).orElseThrow(), snapshots(task.getId()), task.getId(), false);
                }
                task.setStatus(target);
            }
            return enrich(repository.updateTask(task));
        });
    }

    private void reserve(TransportTaskEntity task, ManifestAllocation.Snapshot snap, String actor) {
        if (snap.bags() == null || snap.bags().isEmpty()) throw new BusinessException(409, "empty manifest");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_manifest_assignment WHERE manifest_no=? AND status IN ('RECEIVED','SETTLED')", Long.class, snap.manifestNo()) != 0) {
            throw new BusinessException(409, "received manifest cannot be allocated again; use a new manifest for the next leg");
        }
        String assignment = id();
        jdbc.update("INSERT INTO transport_manifest_assignment (assignment_no,manifest_no,version_no,content_hash,task_id,source_org_code,destination_org_code,facility_route_code,snapshot_json,status,actor_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?)",
                assignment, snap.manifestNo(), snap.versionNo(), snap.contentHash(), task.getId(), snap.originFacilityCode(), snap.destinationFacilityCode(), snap.facilityRouteCode(), write(snap), actor, now());
        jdbc.update("INSERT INTO transport_manifest_current (manifest_no,assignment_no) VALUES (?,?)", snap.manifestNo(), assignment);
        for (var bag : snap.bags()) {
            if (bag.weightGrams() != null && bag.weightGrams() < 0) throw new BusinessException(409, "negative bag weight");
            jdbc.update("INSERT INTO transport_assignment_bag (assignment_no,bag_no,dispatch_bag_id,weight_grams) VALUES (?,?,?,?)", assignment, bag.bagNo(), bag.dispatchBagId(), bag.weightGrams());
            jdbc.update("INSERT INTO transport_bag_current (bag_no,assignment_no) VALUES (?,?)", bag.bagNo(), assignment);
        }
        enqueue(new ManifestAllocation.Command(id(), assignment, snap.manifestNo(), snap.versionNo(), snap.contentHash(), task.getTaskCode(), "ASSIGN", actor, "").normalized());
    }

    private void enqueue(ManifestAllocation.Command command) {
        jdbc.update("INSERT INTO transport_assignment_outbox (request_no,assignment_no,action,payload_json,next_attempt_at,created_at) VALUES (?,?,?,?,?,?)",
                command.requestNo(), command.assignmentNo(), command.action(), write(command), now(), now());
    }

    private ManifestAllocation.Snapshot load(String no, Long bagId) {
        if (no == null || no.isBlank()) {
            if (bagId == null) throw new BusinessException(400, "manifestNo or dispatchBagId is required");
            var bag = dispatch.getBagById(bagId);
            if (bag == null || !bag.isSuccess() || bag.getData() == null) throw new BusinessException(502, "bag lookup failed");
            no = bag.getData().getManifestNo();
        }
        var result = dispatch.manifest(ManifestAllocation.code(no), token);
        if (result == null || !result.isSuccess() || result.getData() == null) throw new BusinessException(409, "sealed manifest lookup failed");
        return result.getData();
    }

    /** Schedule first, then task. Detect an assignment made since the initial unlocked lookup. */
    private TransportTaskEntity lockTask(String code, Long selectedSchedule) {
        var before = repository.findTaskByCode(code).orElseThrow(() -> new BusinessException(404, "task not found"));
        Long schedule = selectedSchedule == null ? before.getScheduleId() : selectedSchedule;
        if (schedule != null) repository.lockSchedule(schedule);
        jdbc.queryForList("SELECT id FROM transport_task WHERE id=? FOR UPDATE", before.getId());
        var task = repository.findTaskById(before.getId()).orElseThrow();
        if (!Objects.equals(before.getScheduleId(), task.getScheduleId())) throw new BusinessException(409, "task schedule changed; retry");
        return task;
    }

    private void validateSchedule(TransportScheduleEntity schedule, List<ManifestAllocation.Snapshot> manifests, Long excludedTask) {
        validateSchedule(schedule, manifests, excludedTask, true);
    }

    private void validateSchedule(TransportScheduleEntity schedule, List<ManifestAllocation.Snapshot> manifests, Long excludedTask, boolean future) {
        if (schedule == null || !"ACTIVE".equals(schedule.getStatus()) || schedule.getDepartureTime() == null || (future && !schedule.getDepartureTime().isAfter(now()))) throw new BusinessException(409, "select an active future schedule");
        var route = repository.findRouteById(schedule.getRouteId()).orElseThrow(() -> new BusinessException(409, "route missing"));
        var asset = repository.findAssetById(schedule.getAssetId()).orElseThrow(() -> new BusinessException(409, "asset missing"));
        String type = switch (route.getTransportType()) { case "AIR" -> "AIRCRAFT"; case "SEA" -> "SHIP"; case "LAND" -> "TRUCK"; default -> ""; };
        if (!"ACTIVE".equals(route.getStatus()) || !List.of("AVAILABLE", "IN_SERVICE").contains(asset.getStatus()) || !type.equals(asset.getType()) || asset.getCapacity() == null) throw new BusinessException(409, "route or vehicle unavailable");
        long weight = 0;
        for (var snap : manifests) {
            if (!Objects.equals(route.getFacilityRouteCode(), snap.facilityRouteCode()) || !Objects.equals(route.getOriginFacilityId(), snap.originFacilityCode()) || !Objects.equals(route.getDestinationFacilityId(), snap.destinationFacilityCode())) throw new BusinessException(409, "schedule does not match manifest leg");
            for (var bag : snap.bags()) {
                if (bag.weightGrams() == null) throw new BusinessException(409, "unknown weight must be resolved before scheduling");
                weight = Math.addExact(weight, bag.weightGrams());
            }
        }
        for (var row : jdbc.queryForList("SELECT b.weight_grams FROM transport_assignment_bag b JOIN transport_manifest_assignment a ON a.assignment_no=b.assignment_no "
                + "JOIN transport_task t ON t.id=a.task_id WHERE t.schedule_id=? AND a.status NOT IN ('RELEASED','RECEIVED','SETTLED') AND (? IS NULL OR t.id<>?)", schedule.getId(), excludedTask, excludedTask)) {
            if (row.get("weight_grams") == null) throw new BusinessException(409, "schedule contains unknown weight");
            weight = Math.addExact(weight, ((Number) row.get("weight_grams")).longValue());
        }
        // Legacy tasks still consume capacity; their rows are retained until the explicit demo rebuild.
        for (var row : jdbc.queryForList("SELECT b.weight_grams FROM transport_task_bag b JOIN transport_task t ON t.id=b.task_id WHERE t.schedule_id=? AND t.status<>'CANCELLED'", schedule.getId())) {
            if (row.get("weight_grams") == null) throw new BusinessException(409, "legacy task has unknown weight");
            weight = Math.addExact(weight, ((Number) row.get("weight_grams")).longValue());
        }
        if (BigDecimal.valueOf(weight).compareTo(asset.getCapacity().multiply(BigDecimal.valueOf(1000))) > 0) throw new BusinessException(409, "schedule capacity exceeded");
    }

    public List<Long> bagIds(Long taskId) {
        return jdbc.query("SELECT b.dispatch_bag_id FROM transport_assignment_bag b JOIN transport_manifest_assignment a ON a.assignment_no=b.assignment_no WHERE a.task_id=? ORDER BY b.bag_no", (rs, n) -> rs.getLong(1), taskId);
    }

    public TransportTaskEntity enrich(TransportTaskEntity task) {
        task.setManifestAssignments(jdbc.query("SELECT a.assignment_no,a.manifest_no,a.version_no,a.status,a.created_at,a.confirmed_at,a.released_at, "
                + "(SELECT o.last_error FROM transport_assignment_outbox o WHERE o.assignment_no=a.assignment_no ORDER BY o.created_at DESC,o.request_no DESC LIMIT 1) last_error, "
                + "(SELECT o.payload_json FROM transport_assignment_outbox o WHERE o.assignment_no=a.assignment_no AND o.action='RELEASE') release_command "
                + ",a.received_at FROM transport_manifest_assignment a WHERE task_id=? ORDER BY a.created_at,a.assignment_no", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assignmentNo", rs.getString(1)); row.put("manifestNo", rs.getString(2)); row.put("versionNo", rs.getInt(3)); row.put("status", rs.getString(4));
            for (int i = 5; i <= 7; i++) row.put(List.of("createdAt", "confirmedAt", "releasedAt").get(i - 5), rs.getTimestamp(i) == null ? null : rs.getTimestamp(i).toLocalDateTime().toInstant(ZoneOffset.UTC));
            row.put("lastError", rs.getString(8));
            row.put("reason", rs.getString(9) == null ? null : read(rs.getString(9), ManifestAllocation.Command.class).reason());
            row.put("receivedAt", rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toLocalDateTime().toInstant(ZoneOffset.UTC));
            return row;
        }, task.getId()));
        var bags = jdbc.query("SELECT b.bag_no FROM transport_assignment_bag b JOIN transport_manifest_assignment a ON a.assignment_no=b.assignment_no WHERE a.task_id=? ORDER BY b.bag_no", (rs, n) -> rs.getString(1), task.getId());
        if (!bags.isEmpty()) task.setBagNos(bags);
        return task;
    }

    private List<String> assignmentNos(Long taskId) { return jdbc.query("SELECT assignment_no FROM transport_manifest_assignment WHERE task_id=? ORDER BY assignment_no", (rs, n) -> rs.getString(1), taskId); }
    private List<ManifestAllocation.Snapshot> snapshots(Long taskId) { return jdbc.query("SELECT snapshot_json FROM transport_manifest_assignment WHERE task_id=? ORDER BY assignment_no", (rs, n) -> read(rs.getString(1), ManifestAllocation.Snapshot.class), taskId); }
    private boolean sameLeg(ManifestAllocation.Snapshot a, ManifestAllocation.Snapshot b) { return a.originFacilityCode().equals(b.originFacilityCode()) && a.destinationFacilityCode().equals(b.destinationFacilityCode()) && a.facilityRouteCode().equals(b.facilityRouteCode()); }
    private void requirePending(TransportTaskEntity task) { if (!List.of("CREATED", "ASSIGNED").contains(task.getStatus())) throw new BusinessException(409, "only unstarted tasks can be changed"); }
    private JwtUserContext user() {
        String header = http.getHeader("Authorization");
        var user = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? JwtTokenUtil.getUserContextFromToken(header.replaceFirst("(?i)^Bearer\\s+", "")) : null;
        if (user == null || user.getRole() == null) throw new BusinessException(401, "valid bearer token required");
        return user;
    }
    private void own(JwtUserContext user, String origin) { if (!AuthConstants.ROLE_ADMIN.equalsIgnoreCase(user.getRole()) && (user.getFacilityCode() == null || !user.getFacilityCode().equalsIgnoreCase(origin))) throw new BusinessException(403, "task belongs to another source facility"); }
    private void ownTask(JwtUserContext user, TransportTaskEntity task) {
        var origins = jdbc.query("SELECT source_org_code FROM transport_manifest_assignment WHERE task_id=?", (rs, n) -> rs.getString(1), task.getId());
        if (origins.isEmpty()) throw new BusinessException(409, "legacy task is read-only; create a manifest task after rebuilding demo data");
        origins.forEach(origin -> own(user, origin));
    }
    private String actor(JwtUserContext user) { return user.getUserId() == null ? user.getUsername() : user.getUserId().toString(); }
    private String id() { return UUID.randomUUID().toString().toUpperCase(Locale.ROOT); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
}
