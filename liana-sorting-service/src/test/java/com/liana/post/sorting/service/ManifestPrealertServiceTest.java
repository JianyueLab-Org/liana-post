package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.ManifestSealCommand;
import com.liana.post.common.dto.sorting.ManifestCancelCommand;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Fast transaction tests; the authoritative MySQL migrations are verified by the database module. */
class ManifestPrealertServiceTest {
    private JdbcTemplate jdbc;
    private ManifestPrealertService service;
    private ManifestSealService seals;
    private ManifestCancelService cancellations;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new ManifestPrealertService(jdbc, new ObjectMapper());
        seals = new ManifestSealService(jdbc, new ObjectMapper());
        cancellations = new ManifestCancelService(jdbc, new ObjectMapper());
        jdbc.execute("CREATE TABLE sorting_manifest (id BIGINT AUTO_INCREMENT PRIMARY KEY, manifest_no VARCHAR(64) UNIQUE NOT NULL,"
                + "manifest_type VARCHAR(16),source_org_code VARCHAR(32),destination_org_code VARCHAR(32),batch_no VARCHAR(64),"
                + "manifest_status VARCHAR(16),prealert_flag INT,version_no INT DEFAULT 0,content_hash CHAR(64),"
                + "expected_package_qty INT DEFAULT 0,expected_item_qty INT DEFAULT 0,updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE sorting_manifest_package (manifest_no VARCHAR(64),package_no VARCHAR(64),expected_seq_no INT,"
                + "expected_weight DECIMAL(12,3),item_status VARCHAR(16),received_at TIMESTAMP(3),PRIMARY KEY(manifest_no,package_no))");
        jdbc.execute("CREATE TABLE sorting_manifest_item (manifest_no VARCHAR(64),item_no VARCHAR(64),expected_package_no VARCHAR(64),"
                + "expected_seq_no INT,expected_route_code VARCHAR(64),expected_qty INT,item_status VARCHAR(16),PRIMARY KEY(manifest_no,item_no))");
        jdbc.execute("CREATE TABLE sorting_manifest_version (manifest_no VARCHAR(64),version_no INT,content_hash CHAR(64),"
                + "expected_package_qty INT,expected_item_qty INT,snapshot_json VARCHAR(100000),recorded_at TIMESTAMP(3),PRIMARY KEY(manifest_no,version_no))");
        jdbc.execute("CREATE TABLE package_item_line (manifest_no VARCHAR(64))");
        // Mirrors sorting V3; authoritative MySQL DDL is exercised separately.
        jdbc.execute("CREATE TABLE sorting_manifest_seal (manifest_no VARCHAR(64) PRIMARY KEY,version_no INT,request_no VARCHAR(64) UNIQUE,"
                + "request_hash CHAR(64),request_json VARCHAR(10000),content_hash CHAR(64),source_org_code VARCHAR(64),actor_id VARCHAR(64),sealed_at TIMESTAMP(3),"
                + "FOREIGN KEY (manifest_no,version_no) REFERENCES sorting_manifest_version(manifest_no,version_no))");
        jdbc.execute("CREATE TABLE sorting_manifest_cancel (manifest_no VARCHAR(64) PRIMARY KEY,version_no INT,request_no VARCHAR(64) UNIQUE,"
                + "request_hash CHAR(64),request_json VARCHAR(10000),content_hash CHAR(64),source_org_code VARCHAR(64),actor_id VARCHAR(64),reason VARCHAR(512),cancelled_at TIMESTAMP(3),"
                + "FOREIGN KEY (manifest_no,version_no) REFERENCES sorting_manifest_version(manifest_no,version_no))");
        jdbc.execute("CREATE TABLE sorting_total_package (manifest_no VARCHAR(64),received_at TIMESTAMP(3),opened_at TIMESTAMP(3),dispatched_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE discrepancy_verification (manifest_no VARCHAR(64))");
        jdbc.execute("CREATE TABLE sorting_bag_handoff (manifest_no VARCHAR(64),bag_no VARCHAR(64),status VARCHAR(16),completed_at TIMESTAMP(3))");
    }

    @Test
    void replacesRemovedRowsButRetainsCompleteHistoryAndDoesNotCountPrealertAsReceived() {
        accept(request(1, "B-ONE", "U-ONE"));
        accept(request(2, "B-TWO", "U-TWO"));
        assertEquals(List.of("B-TWO"), jdbc.queryForList("SELECT package_no FROM sorting_manifest_package", String.class));
        assertEquals(List.of("U-TWO"), jdbc.queryForList("SELECT item_no FROM sorting_manifest_item", String.class));
        assertEquals("EXPECTED", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_package", String.class));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_package WHERE received_at IS NOT NULL"));
        var versions = service.versions("m-test");
        assertEquals(2, versions.size());
        assertEquals("B-ONE", versions.get(0).snapshot().path("totalPackages").get(0).path("packageNo").asText());
        assertEquals("LN-OR-01", versions.get(0).snapshot().path("originNode").asText());
    }

    @Test
    void delayedRetryIsIdempotentAndSameVersionWithChangedContentConflicts() {
        accept(request(1, "B-ONE", "U-ONE"));
        accept(request(2, "B-TWO", "U-TWO"));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CHECKED'");
        jdbc.update("UPDATE sorting_manifest_package SET item_status='RECEIVED',received_at=CURRENT_TIMESTAMP");
        accept(request(1, "B-ONE", "U-ONE"));
        accept(request(2, "B-TWO", "U-TWO"));
        assertEquals("CHECKED", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT version_no FROM sorting_manifest", Integer.class));
        assertEquals("RECEIVED", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_package", String.class));
        assertThrows(BusinessException.class, () -> accept(request(2, "B-DIFFERENT", "U-TWO")));
        assertEquals(2, service.versions("M-TEST").size());
    }

    @Test
    void rejectsVersionGapsEndpointChangesAndUpdatesAfterProcessingStarts() {
        assertThrows(BusinessException.class, () -> accept(request(2, "B-ONE", "U-ONE")));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest"));
        accept(request(1, "B-ONE", "U-ONE"));
        var changed = request(2, "B-TWO", "U-TWO");
        changed.setTargetNode("LN-NAM-AIR");
        assertThrows(BusinessException.class, () -> accept(changed));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='SEALED'");
        assertThrows(BusinessException.class, () -> accept(request(2, "B-TWO", "U-TWO")));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='PREALERT'");
        jdbc.update("UPDATE sorting_manifest_package SET item_status='RECEIVED'");
        assertThrows(BusinessException.class, () -> accept(request(2, "B-TWO", "U-TWO")));
        assertEquals(1, service.versions("M-TEST").size());
    }

    @Test
    void failureMidwayRollsBackRemovedAndInsertedDetailsAndVersion() {
        accept(request(1, "B-ONE", "U-ONE"));
        jdbc.execute("ALTER TABLE sorting_manifest_item ADD CONSTRAINT reject_bad CHECK(item_no<>'FAIL')");
        assertThrows(RuntimeException.class, () -> accept(request(2, "B-TWO", "FAIL")));
        assertEquals("B-ONE", jdbc.queryForObject("SELECT package_no FROM sorting_manifest_package", String.class));
        assertEquals("U-ONE", jdbc.queryForObject("SELECT item_no FROM sorting_manifest_item", String.class));
        assertEquals(1, service.versions("M-TEST").size());
        assertEquals(1, jdbc.queryForObject("SELECT version_no FROM sorting_manifest", Integer.class));
    }

    @Test
    void concurrentFirstDeliveryCreatesOneVersion() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> accept(request(1, "B-ONE", "U-ONE"))));
            for (var job : jobs) job.get();
            assertEquals(1, service.versions("M-TEST").size());
            assertEquals(1, count("SELECT COUNT(*) FROM sorting_manifest_package"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void rejectsDuplicateMailAcrossPackagesAndInvalidWeightsBeforeWriting() {
        var r = request(1, "B-ONE", "U-ONE");
        var duplicate = request(1, "B-TWO", "u-one").getTotalPackages().get(0);
        r.setTotalPackages(List.of(r.getTotalPackages().get(0), duplicate));
        assertThrows(BusinessException.class, () -> accept(r));
        var badWeight = request(1, "B-ONE", "U-ONE");
        badWeight.setTotalWeight(new BigDecimal("101"));
        assertThrows(BusinessException.class, () -> accept(badWeight));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest"));
    }

    private void accept(ManifestDTO request) { tx.executeWithoutResult(s -> service.accept(request)); }

    @Test
    void cancellingSealedManifestRetainsItsContentsAndSealWhileBlockingFurtherOperations() {
        accept(request(1, "B-ONE", "U-ONE"));
        var seal = new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk");
        var sealed = tx.execute(s -> seals.seal(seal));
        var command = cancellation("C1", 1, "取消本次配发");
        var first = tx.execute(s -> cancellations.cancel(command));
        assertEquals(first, tx.execute(s -> cancellations.cancel(command)));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        assertEquals("取消本次配发", first.reason());
        assertEquals(sealed, seals.receipt("M-TEST"));
        assertEquals(1, service.versions("M-TEST").size());
        assertEquals("B-ONE", jdbc.queryForObject("SELECT package_no FROM sorting_manifest_package", String.class));
        assertEquals("U-ONE", jdbc.queryForObject("SELECT item_no FROM sorting_manifest_item", String.class));
        assertFalse(service.isSealed("M-TEST"));
        assertThrows(BusinessException.class, () -> service.requireSealed("M-TEST"));
        accept(request(1, "B-ONE", "U-ONE")); // Late prealert and seal retries preserve history, never reactivate.
        assertEquals(sealed, tx.execute(s -> seals.seal(seal)));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        assertThrows(BusinessException.class, () -> accept(request(2, "B-TWO", "U-TWO")));
        assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(new ManifestSealCommand("S2", "M-TEST", 1, "LN-OR-01", "clerk"))));
    }

    @Test
    void canCancelAcknowledgedPrealertWithoutInventingASealReceipt() {
        accept(request(1, "B-ONE", "U-ONE"));
        var receipt = tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "配发安排撤销")));
        assertNotNull(receipt);
        assertNull(seals.receipt("M-TEST"));
        assertEquals(1, count("SELECT COUNT(*) FROM sorting_manifest_cancel"));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_package WHERE received_at IS NOT NULL"));
    }

    @Test
    void cancellationRejectsWrongFacilityStaleVersionMissingReasonAndReusedRequest() {
        accept(request(1, "B-ONE", "U-ONE"));
        assertEquals(403, assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(
                new ManifestCancelCommand("C1", "M-TEST", 1, "LN-NAM-HQ", "clerk", "wrong facility")))).getCode());
        assertEquals(409, assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 2, "stale version")))).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "  ")))).getCode());
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_cancel"));
        tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "original")));
        assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "changed"))));
        assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C2", 1, "original"))));
    }

    @Test
    void processingFactsPreventCancellationEvenWhenStatusStillSaysPrealert() {
        accept(request(1, "B-ONE", "U-ONE"));
        var scenarios = List.of(
                List.of("UPDATE sorting_manifest_package SET received_at=CURRENT_TIMESTAMP", "UPDATE sorting_manifest_package SET received_at=NULL"),
                List.of("UPDATE sorting_manifest_item SET item_status='RECEIVED'", "UPDATE sorting_manifest_item SET item_status='EXPECTED'"),
                List.of("INSERT INTO package_item_line VALUES ('M-TEST')", "DELETE FROM package_item_line"),
                List.of("INSERT INTO sorting_total_package (manifest_no,opened_at) VALUES ('M-TEST',CURRENT_TIMESTAMP)", "DELETE FROM sorting_total_package"),
                List.of("INSERT INTO discrepancy_verification VALUES ('M-TEST')", "DELETE FROM discrepancy_verification"));
        for (var scenario : scenarios) {
            jdbc.update(scenario.get(0));
            assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "too late"))));
            assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_cancel"));
            jdbc.update(scenario.get(1));
        }
        for (String state : List.of("DEPARTED", "ARRIVED", "RECEIVING", "CHECKED", "CLOSED")) {
            jdbc.update("UPDATE sorting_manifest SET manifest_status=?", state);
            assertThrows(BusinessException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "too late"))));
        }
    }

    @Test
    void cancellationReceiptAndStatusRollBackTogetherOnFailure() {
        accept(request(1, "B-ONE", "U-ONE"));
        jdbc.execute("ALTER TABLE sorting_manifest ADD CONSTRAINT reject_cancel CHECK(manifest_status<>'CANCELLED')");
        assertThrows(RuntimeException.class, () -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "cancel"))));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_cancel"));
        assertEquals("PREALERT", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        jdbc.execute("ALTER TABLE sorting_manifest DROP CONSTRAINT reject_cancel");
        assertNotNull(tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "cancel"))));
    }

    @Test
    void concurrentCancellationRequestsCreateOneReceipt() throws Exception {
        accept(request(1, "B-ONE", "U-ONE"));
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<ManifestCancelService.Receipt>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "cancel")))));
            var receipt = jobs.get(0).get();
            for (var job : jobs) assertEquals(receipt, job.get());
            assertEquals(1, count("SELECT COUNT(*) FROM sorting_manifest_cancel"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void receivingAndCancellationCannotBothCommit() throws Exception {
        accept(request(1, "B-ONE", "U-ONE"));
        tx.execute(s -> seals.seal(new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk")));
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var cancel = pool.submit(() -> {
                start.await();
                try { tx.execute(s -> cancellations.cancel(cancellation("C1", 1, "cancel"))); return true; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
            });
            var receive = pool.submit(() -> {
                start.await();
                try {
                    tx.executeWithoutResult(s -> {
                        jdbc.queryForMap("SELECT * FROM sorting_manifest WHERE manifest_no='M-TEST' FOR UPDATE");
                        service.requireSealed("M-TEST");
                        jdbc.update("UPDATE sorting_manifest_package SET item_status='RECEIVED',received_at=CURRENT_TIMESTAMP");
                        jdbc.update("UPDATE sorting_manifest SET manifest_status='RECEIVING'");
                    });
                    return true;
                } catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
            });
            start.countDown();
            boolean cancelled = cancel.get(), received = receive.get();
            assertNotEquals(cancelled, received);
            assertEquals(cancelled ? "CANCELLED" : "RECEIVING", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
            assertEquals(cancelled ? 0 : 1, count("SELECT COUNT(*) FROM sorting_manifest_package WHERE received_at IS NOT NULL"));
        } finally { pool.shutdownNow(); }
    }

    private ManifestCancelCommand cancellation(String requestNo, int version, String reason) {
        return new ManifestCancelCommand(requestNo, "M-TEST", version, "LN-OR-01", "clerk", reason);
    }

    @Test
    void sealPinsVersionAndReceiptAndLateRetryDoesNotRewindReceiving() {
        accept(request(1, "B-ONE", "U-ONE"));
        assertThrows(BusinessException.class, () -> service.requireSealed("M-TEST"));
        var command = new ManifestSealCommand("SEAL-1", "M-TEST", 1, "LN-OR-01", "clerk");
        var first = tx.execute(s -> seals.seal(command));
        assertNotNull(first);
        assertEquals("SEALED", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        assertDoesNotThrow(() -> service.requireSealed("M-TEST"));
        assertThrows(BusinessException.class, () -> accept(request(2, "B-TWO", "U-TWO")));
        accept(request(1, "B-ONE", "U-ONE"));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='RECEIVING'");
        assertEquals(first, tx.execute(s -> seals.seal(command)));
        assertEquals("RECEIVING", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
        assertEquals(1, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
        assertEquals("EXPECTED", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_package", String.class));
    }

    @Test
    void sealRejectsWrongSourceStaleVersionAndReusedCommandWithoutChangingState() {
        accept(request(1, "B-ONE", "U-ONE"));
        assertEquals(403, assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(
                new ManifestSealCommand("S1", "M-TEST", 1, "LN-NAM-HQ", "clerk")))).getCode());
        assertEquals(409, assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(
                new ManifestSealCommand("S1", "M-TEST", 2, "LN-OR-01", "clerk")))).getCode());
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
        tx.execute(s -> seals.seal(new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk")));
        assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(
                new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "changed-actor"))));
        assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(
                new ManifestSealCommand("S2", "M-TEST", 1, "LN-OR-01", "clerk"))));
    }

    @Test
    void failedSealStateWriteRollsBackReceiptAndRetryCanSucceed() {
        accept(request(1, "B-ONE", "U-ONE"));
        jdbc.execute("ALTER TABLE sorting_manifest ADD CONSTRAINT reject_seal CHECK(manifest_status<>'SEALED')");
        var command = new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk");
        assertThrows(RuntimeException.class, () -> tx.execute(s -> seals.seal(command)));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
        jdbc.execute("ALTER TABLE sorting_manifest DROP CONSTRAINT reject_seal");
        assertNotNull(tx.execute(s -> seals.seal(command)));
    }

    @Test
    void concurrentSealsYieldOneImmutableReceipt() throws Exception {
        accept(request(1, "B-ONE", "U-ONE"));
        var command = new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk");
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<ManifestSealService.Receipt>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> tx.execute(s -> seals.seal(command))));
            var receipt = jobs.get(0).get();
            for (var job : jobs) assertEquals(receipt, job.get());
            assertEquals(1, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void versionChangeRacingSealLeavesEitherSealedOldVersionOrUnsealedNewVersion() throws Exception {
        accept(request(1, "B-ONE", "U-ONE"));
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var seal = pool.submit(() -> {
                start.await();
                try { tx.execute(s -> seals.seal(new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk"))); return true; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
            });
            var update = pool.submit(() -> {
                start.await();
                try { accept(request(2, "B-TWO", "U-TWO")); return true; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
            });
            start.countDown();
            boolean sealed = seal.get(), updated = update.get();
            assertNotEquals(sealed, updated);
            assertEquals(sealed ? 1 : 2, jdbc.queryForObject("SELECT version_no FROM sorting_manifest", Integer.class));
            assertEquals(sealed, service.isSealed("M-TEST"));
            assertEquals(sealed ? "B-ONE" : "B-TWO", jdbc.queryForObject("SELECT package_no FROM sorting_manifest_package", String.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void sealDoesNotAcceptExistingProcessingFactsHiddenBehindPrealertStatus() {
        accept(request(1, "B-ONE", "U-ONE"));
        jdbc.update("UPDATE sorting_manifest_package SET received_at=CURRENT_TIMESTAMP");
        assertThrows(BusinessException.class, () -> tx.execute(s -> seals.seal(
                new ManifestSealCommand("S1", "M-TEST", 1, "LN-OR-01", "clerk"))));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
        assertEquals("PREALERT", jdbc.queryForObject("SELECT manifest_status FROM sorting_manifest", String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyRebagDelegatesToReliableStructuredPackingWithoutCreatingOrSealingAManifest() {
        var repository = org.mockito.Mockito.mock(com.liana.post.sorting.repository.SortingRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.liana.post.sorting.client.OmsClient> oms = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<com.liana.post.sorting.client.TrackingClient> tracking = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        var repacking = org.mockito.Mockito.mock(com.liana.post.sorting.service.RepackingService.class);
        var sorting = new com.liana.post.sorting.service.impl.SortingServiceImpl(repository, null, oms, tracking, null, null, service, seals, null,
                org.mockito.Mockito.mock(UnpackingService.class), repacking);
        var request = new com.liana.post.sorting.model.dto.SortingRebagRequest();
        request.setStationCode("LN-OR-RC"); request.setRouteCode("LN-NAM-AIR"); request.setSourcePackageNo("SOURCE-BAG");
        request.setPackageLevel("TRANSIT"); request.setItemNos(List.of("U-ONE")); request.setOperatorId("untrusted-client-operator");
        sorting.reBag(request);
        org.mockito.Mockito.verify(repacking).reserve(null, "LN-OR-RC", null, "LN-NAM-AIR", "SOURCE-BAG", List.of("U-ONE"));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest"));
        assertEquals(0, count("SELECT COUNT(*) FROM sorting_manifest_seal"));
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private ManifestDTO request(int version, String bag, String mail) {
        var r = new ManifestDTO();
        r.setManifestCode("M-TEST"); r.setBatchNo("D-TEST"); r.setVersionNo(version);
        r.setOriginNode("LN-OR-01"); r.setTargetNode("LN-OR-RC");
        var p = new TotalPackageDTO(); p.setPackageNo(bag); p.setPackageWeight(new BigDecimal("100.000"));
        var item = new TotalPackageDTO.ManifestItemDTO(); item.setItemNo(mail); item.setSeqNo(1);
        p.setItems(List.of(item)); r.setTotalPackages(List.of(p));
        return r;
    }
}
