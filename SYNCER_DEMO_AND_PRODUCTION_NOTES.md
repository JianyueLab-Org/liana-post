# Syncer Demo Closure and Production Notes

## Current Demo Scope

This project now includes a minimal, demonstrable Syncer loop for the weak-network synchronization design:

1. A sync plan is submitted through `POST /api/syncer/plans`.
2. The plan is persisted as one or more `outbox_message` rows.
3. The outbox scan task converts eligible outbox rows into `sync_task` rows.
4. The retry task simulates delivery of pending sync tasks.
5. Failed deliveries are recorded in `retry_record` and retried according to the backoff policy.
6. The latest outbox, task, retry, and status-count data can be queried through management APIs.

This is a demonstration-grade implementation. It proves the Outbox + SyncTask + RetryRecord mechanism and makes the state changes visible during a presentation.

## Files Changed

- `liana-syncer/pom.xml`
  - Added `spring-boot-starter-web` so Syncer exposes demo management APIs.

- `liana-syncer/src/main/resources/application.yml`
  - Changed Syncer default port to `8089`.
  - Added `syncer.demo.dispatch-on-create=true` so submitted demo events immediately trigger one outbox scan and one task dispatch attempt.

- `docker-compose.demo.yml`
  - Exposes Syncer on `localhost:8089` for direct demo access.

- `liana-syncer/src/main/java/com/liana/post/syncer/controller/SyncerController.java`
  - Added APIs for submitting sync plans, triggering scans/retries, querying records, and creating demo events.

- `liana-syncer/src/main/java/com/liana/post/syncer/service/impl/SyncerServiceImpl.java`
  - Implemented outbox creation, scanning, sync task creation, simulated delivery, failure recording, retry scheduling, and dashboard queries.

- `liana-syncer/src/main/java/com/liana/post/syncer/model/dto/SyncPlanDto.java`
  - Added validation and demo flags: `failFirstAttempt`, `alwaysFail`, and `attributes`.

- `liana-syncer/src/main/java/com/liana/post/syncer/handler/SyncerExceptionHandler.java`
  - Enabled REST exception handling for business, validation, and system errors.

- `liana-syncer/src/main/java/com/liana/post/syncer/constant/SyncerConstants.java`
  - Added `NEW` status.

## Demo API

Base URL when using Docker Compose demo:

```text
http://localhost:8089/api/syncer
```

Useful endpoints:

```text
POST /api/syncer/demo/success
POST /api/syncer/demo/fail-once
POST /api/syncer/plans
POST /api/syncer/outbox/scan
POST /api/syncer/tasks/retry
GET  /api/syncer/summary
GET  /api/syncer/outbox
GET  /api/syncer/tasks
GET  /api/syncer/retries
```

Example custom plan:

```json
{
  "sourceService": "liana-dispatch-service",
  "targetService": "liana-tracking-service",
  "bizType": "DISPATCH_BAG_SEALED",
  "bizIds": ["BAG-DEMO-001"],
  "failFirstAttempt": true,
  "alwaysFail": false,
  "attributes": {
    "bagNo": "BAG-DEMO-001",
    "operator": "syncer-demo"
  }
}
```

## Suggested Demo Flow

1. Start the demo environment:

```powershell
docker compose -f docker-compose.demo.yml up -d --build
```

2. Open the Syncer summary:

```text
GET http://localhost:8089/api/syncer/summary
```

3. Create a successful sync event:

```text
POST http://localhost:8089/api/syncer/demo/success
```

4. Refresh summary and show:

- `outbox_message`: `SUCCESS`
- `sync_task`: `SUCCESS`
- `retry_record`: no new failure for this event

5. Create a fail-once event:

```text
POST http://localhost:8089/api/syncer/demo/fail-once
```

6. Refresh summary and show:

- `outbox_message`: `SUCCESS`
- `sync_task`: `FAILED`
- `retry_record`: `PENDING`

7. Wait for the retry time or manually trigger:

```text
POST http://localhost:8089/api/syncer/tasks/retry
```

8. Refresh summary and show that the same sync task becomes `SUCCESS`.

## Presentation Wording

Recommended wording:

> The Syncer module implements a lightweight Outbox synchronization prototype. Business events are first persisted into an outbox table, then converted into sync tasks by a scheduled scanner. Failed task deliveries are recorded and retried with a backoff policy. This demonstrates how the system can tolerate weak networks and recover asynchronously.

Avoid saying:

> The project already implements a production-grade distributed transaction system.

Accurate boundary:

> The current implementation is a demonstrable synchronization compensation loop. Real cross-service event publishing, idempotent consumers, dead-letter handling, and operational repair screens are planned production enhancements.

## Production-Level Next Steps

1. Move outbox writes into each business service transaction.
   - Example: OMS status update and tracking event creation should insert an outbox row in the same local database transaction as the business update.

2. Define a stable event contract.
   - Add event version, aggregate type, aggregate id, event time, trace id, source facility, and idempotency key.

3. Replace simulated delivery with real dispatch.
   - Options: OpenFeign callback, message queue, or pull-based target service consumption.
   - For unstable island networks, message queue or pull-based synchronization is usually more resilient than direct synchronous calls.

4. Add idempotent consumers.
   - Each target service should record consumed event ids and ignore duplicates.

5. Add dead-letter and manual repair.
   - After max retries, move records to a dead-letter state.
   - Provide a management page for retry, skip, edit payload, and audit trail.

6. Add observability.
   - Metrics: pending task count, failed task count, retry latency, oldest unprocessed outbox age.
   - Logs should include `msgId`, `taskNo`, `bizType`, and `bizId`.

7. Separate demo and production configuration.
   - Keep `syncer.demo.dispatch-on-create` disabled in production.
   - Production should rely on scheduled workers, distributed locks, and batch-size controls.

8. Add concurrency protection.
   - Use row-level locking, optimistic version checks, or a claim-token field so multiple Syncer instances do not process the same task twice.

9. Add integration tests.
   - Cover outbox creation, task generation, one-time failure recovery, max retry failure, and duplicate event handling.

