package com.liana.post.postage.tariff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Repository
public class TariffStore {
    public record Version(String versionId, String status, int revision, Instant effectiveFrom,
                          TariffDefinition definition, String updatedBy, Instant updatedAt,
                          String publishedBy, Instant publishedAt) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TariffStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    // Shared database row serializes publication/quotation even across multiple service instances.
    public void lock() {
        jdbc.queryForObject("SELECT id FROM postage_tariff_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public List<Version> versions() {
        return jdbc.query("SELECT * FROM postage_tariff_version ORDER BY updated_at DESC, version_id", (rs, n) ->
                new Version(rs.getString("version_id"), rs.getString("status"), rs.getInt("revision"),
                        instant(rs.getObject("effective_from")), read(rs.getString("definition_json"), TariffDefinition.class),
                        rs.getString("updated_by"), instant(rs.getObject("updated_at")),
                        rs.getString("published_by"), instant(rs.getObject("published_at"))));
    }

    public Version version(String id) {
        return versions().stream().filter(v -> v.versionId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资费版本不存在"));
    }

    public void insert(String id, TariffDefinition definition, String actor, Instant now) {
        jdbc.update("INSERT INTO postage_tariff_version (version_id,status,revision,definition_json,updated_by,updated_at) VALUES (?,'DRAFT',1,?,?,?)",
                id, write(definition), actor, now.toEpochMilli());
    }

    public void update(String id, TariffDefinition definition, String actor, Instant now) {
        jdbc.update("UPDATE postage_tariff_version SET definition_json=?, revision=revision+1, updated_by=?, updated_at=? WHERE version_id=? AND status='DRAFT'",
                write(definition), actor, now.toEpochMilli(), id);
    }

    public void publish(String id, Instant effective, String actor, Instant now) {
        jdbc.update("UPDATE postage_tariff_version SET status='PUBLISHED', revision=revision+1, effective_from=?, published_by=?, published_at=?, updated_by=?, updated_at=? WHERE version_id=? AND status='DRAFT'",
                effective.toEpochMilli(), actor, now.toEpochMilli(), actor, now.toEpochMilli(), id);
    }

    public void saveQuote(QuoteModels.Snapshot s) {
        jdbc.update("INSERT INTO postage_quote (quote_id,tariff_version,created_at,expires_at,created_by,snapshot_json) VALUES (?,?,?,?,?,?)",
                s.quoteId(), s.tariffVersion(), s.createdAt().toEpochMilli(), s.expiresAt().toEpochMilli(), s.createdBy(), write(s));
    }

    public QuoteModels.Snapshot quote(String id) {
        List<String> rows = jdbc.query("SELECT snapshot_json FROM postage_quote WHERE quote_id=?", (rs, n) -> rs.getString(1), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "报价不存在");
        return read(rows.get(0), QuoteModels.Snapshot.class);
    }

    private Instant instant(Object value) { return value == null ? null : Instant.ofEpochMilli(((Number) value).longValue()); }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize tariff data", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read stored tariff data", e); }
    }
}
