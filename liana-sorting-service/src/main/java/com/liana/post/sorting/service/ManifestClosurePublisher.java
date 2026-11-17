package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestClosure;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import com.liana.post.sorting.client.TransportReceiptClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ManifestClosurePublisher {
    public record Delivery(String manifestNo, String destination, String payload, int attempts) { }
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DispatchLifecycleClient dispatch;
    private final TransportReceiptClient transport; private final String token;
    public ManifestClosurePublisher(JdbcTemplate jdbc, ObjectMapper json, DispatchLifecycleClient dispatch, TransportReceiptClient transport,
            @Value("${manifest.internal-token:}") String token) {
        this.jdbc=jdbc; this.json=json; this.dispatch=dispatch; this.transport=transport; this.token=token;
    }
    @Scheduled(fixedDelayString="${manifest.closure-publish-delay-ms:5000}")
    public void publish() { publishDue(); }
    public int publishDue() {
        int sent=0;
        for (var d: due()) {
            if (!claim(d)) continue;
            try {
                var command=json.readValue(d.payload(), ManifestClosure.Command.class);
                var result="DISPATCH".equals(d.destination()) ? dispatch.close(command,token) : transport.close(command,token);
                if (result==null || !result.isSuccess() || result.getData()==null) { fail(d, result==null ? "empty closure response" : result.getMessage(), result!=null && conflict(result.getCode())); continue; }
                var ack=result.getData();
                if (!command.equals(ack.command()) || !d.destination().equals(ack.destination()) || ack.confirmedAt()==null
                        || ack.confirmedAt().isAfter(Instant.now().plusSeconds(60))) { fail(d, "closure ACK differs from fixed command/destination", true); continue; }
                if (jdbc.update("UPDATE sorting_close_outbox SET status='SENT',receipt_json=?,confirmed_at=?,last_error=NULL WHERE manifest_no=? AND destination=? AND status='SENDING' AND attempt_count=?",
                        write(ack), LocalDateTime.ofInstant(ack.confirmedAt(),ZoneOffset.UTC),d.manifestNo(),d.destination(),d.attempts()+1)==1) {
                    jdbc.update("UPDATE sorting_manifest SET manifest_status='CLOSED',updated_at=? WHERE manifest_no=? AND manifest_status='CLOSING' AND (SELECT COUNT(*) FROM sorting_close_outbox WHERE manifest_no=? AND status<>'SENT')=0",
                            LocalDateTime.now(ZoneOffset.UTC),d.manifestNo(),d.manifestNo()); sent++;
                }
            } catch (feign.FeignException e) { fail(d,e.getMessage(),conflict(e.status())); }
            catch (Exception e) { fail(d,e.getMessage(),false); }
        }
        // Also recover a crash between the last durable ACK and the lifecycle projection update.
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CLOSED',updated_at=? WHERE manifest_status='CLOSING' AND (SELECT COUNT(*) FROM sorting_close_outbox o WHERE o.manifest_no=sorting_manifest.manifest_no AND o.status='SENT')=2",
                LocalDateTime.now(ZoneOffset.UTC));
        return sent;
    }
    public List<Delivery> due() {
        return jdbc.query("SELECT o.manifest_no,o.destination,c.payload_json,o.attempt_count FROM sorting_close_outbox o JOIN sorting_manifest_close c ON c.manifest_no=o.manifest_no WHERE o.status IN ('PENDING','RETRY','SENDING') AND o.next_attempt_at<=? ORDER BY o.next_attempt_at,o.manifest_no,o.destination LIMIT 20",
                (rs,n)->new Delivery(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)),LocalDateTime.now(ZoneOffset.UTC));
    }
    private boolean claim(Delivery d) { return jdbc.update("UPDATE sorting_close_outbox SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? WHERE manifest_no=? AND destination=? AND attempt_count=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=?",
            LocalDateTime.now(ZoneOffset.UTC).plusSeconds(60),d.manifestNo(),d.destination(),d.attempts(),LocalDateTime.now(ZoneOffset.UTC))==1; }
    private void fail(Delivery d, String reason, boolean conflict) {
        String error=reason==null ? "closure delivery failed" : reason.substring(0,Math.min(512,reason.length()));
        jdbc.update("UPDATE sorting_close_outbox SET status=?,next_attempt_at=?,last_error=? WHERE manifest_no=? AND destination=? AND status='SENDING' AND attempt_count=?",
            conflict ? "CONFLICT" : "RETRY",LocalDateTime.now(ZoneOffset.UTC).plusSeconds(Math.min(3600,5L << Math.min(9,d.attempts()))),error,d.manifestNo(),d.destination(),d.attempts()+1);
    }
    private boolean conflict(Integer code) { return code!=null && (code==400 || code==403 || code==409); }
    private String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
}
