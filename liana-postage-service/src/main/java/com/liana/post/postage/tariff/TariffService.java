package com.liana.post.postage.tariff;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TariffService {
    private final TariffStore store;
    private final TariffValidator validator;
    private final TariffCalculator calculator;
    private final Clock clock;
    public TariffService(TariffStore store, TariffValidator validator, TariffCalculator calculator, Clock clock) {
        this.store = store; this.validator = validator; this.calculator = calculator; this.clock = clock;
    }
    public List<TariffStore.Version> versions() { return store.versions(); }
    public TariffStore.Version version(String id) { return store.version(id); }
    public TariffStore.Version current() { return active(store.versions(), now()); }

    public TariffStore.Version create(String id, TariffDefinition d, String actor) {
        if (id == null || !id.matches("[A-Z0-9_-]{1,64}")) throw new IllegalArgumentException("版本标识无效");
        validator.validate(d);
        store.lock();
        if (store.versions().stream().anyMatch(v -> v.versionId().equals(id))) conflict("版本标识已存在");
        store.insert(id, d, actor, now());
        return store.version(id);
    }

    public TariffStore.Version update(String id, int revision, TariffDefinition d, String actor) {
        validator.validate(d);
        store.lock();
        checkDraft(store.version(id), revision);
        store.update(id, d, actor, now());
        return store.version(id);
    }

    public TariffStore.Version publish(String id, int revision, Instant requestedEffective, String actor) {
        store.lock();
        var v = store.version(id);
        checkDraft(v, revision);
        validator.validate(v.definition());
        Instant now = now();
        Instant effective = requestedEffective == null ? now : requestedEffective.truncatedTo(ChronoUnit.MILLIS);
        if (effective.isBefore(now)) throw new IllegalArgumentException("不允许追溯发布；留空为立即生效");
        // Append only: publishing cannot change past pricing or replace an already scheduled version.
        for (var existing : store.versions()) {
            if ("PUBLISHED".equals(existing.status()) && !effective.isAfter(existing.effectiveFrom())) {
                conflict("生效时间必须晚于所有已发布版本（包括预约版本）");
            }
        }
        store.publish(id, effective, actor, now);
        return store.version(id);
    }

    public QuoteModels.Snapshot quote(QuoteModels.Request request, String actor) {
        store.lock();
        Instant now = now();
        var versions = store.versions();
        var v = active(versions, now);
        var calculation = calculator.calculate(v.definition(), request);
        Instant expires = now.plus(30, ChronoUnit.MINUTES);
        for (var next : versions) {
            if ("PUBLISHED".equals(next.status()) && next.effectiveFrom().isAfter(now)
                    && next.effectiveFrom().isBefore(expires)) expires = next.effectiveFrom();
        }
        var snapshot = new QuoteModels.Snapshot(UUID.randomUUID().toString(), v.versionId(),
                v.definition().currency(), request, calculation, now, expires, actor);
        store.saveQuote(snapshot);
        return snapshot;
    }

    public QuoteModels.View quote(String id) {
        store.lock();
        var s = store.quote(id);
        Instant now = now();
        boolean valid = now.isBefore(s.expiresAt()) && active(store.versions(), now).versionId().equals(s.tariffVersion());
        return new QuoteModels.View(s, valid);
    }

    /** Explicit initial import, never overwrites an existing version or populates a nonempty catalog. */
    public void seed(String id, Instant effectiveFrom, TariffDefinition definition) {
        store.lock();
        if (!store.versions().isEmpty()) return;
        if (id == null || !id.matches("[A-Z0-9_-]{1,64}") || effectiveFrom == null) {
            throw new IllegalArgumentException("初始发布配置无效");
        }
        validator.validate(definition);
        Instant now = now();
        store.insert(id, definition, "SYSTEM_INITIAL_IMPORT", now);
        store.publish(id, effectiveFrom.truncatedTo(ChronoUnit.MILLIS), "SYSTEM_INITIAL_IMPORT", now);
    }

    private TariffStore.Version active(List<TariffStore.Version> versions, Instant now) {
        return versions.stream().filter(v -> "PUBLISHED".equals(v.status()) && !v.effectiveFrom().isAfter(now))
                .max(Comparator.comparing(TariffStore.Version::effectiveFrom))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "当前没有生效的资费版本"));
    }
    private void checkDraft(TariffStore.Version v, int revision) {
        if (!"DRAFT".equals(v.status())) conflict("已发布资费不可修改，请创建新版本");
        if (v.revision() != revision) conflict("草稿已更新，请重新读取版本");
    }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }
}
