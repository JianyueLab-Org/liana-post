# Liana Pacific Postal System API Contract

## Auth
- POST /auth/login
- GET /auth/me
- GET /auth/permissions

## Mail
- POST /mail/create
- GET /mail/list
- GET /mail/{id}
- PUT /mail/{id}

## Dispatch
- GET /dispatch/bags
- GET /dispatch/batches
- POST /dispatch/bag/create
- POST /dispatch/bag/confirm
- POST /dispatch/handoff

## Tracking
- GET /tracking/{mailNo}
- GET /tracking/events/{mailNo}

## Facility
- GET /facility/offices
- GET /facility/hubs
- GET /facility/routes

## System
- GET /system/users
- GET /system/roles
- GET /system/permissions

## Syncer
- GET /sync/outbox
- GET /sync/tasks

## Mock Rules
- Mark frontend-first endpoints with `mock: true`.
- Return JSON examples for each mock endpoint.
- Tracking is read-only and must not expose write operations.
- Dispatch data is accessed via REST API or future event interfaces only.
