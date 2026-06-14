# Liana-Post Architecture Evaluation Report

## 1. Scope
This report reflects the current state of the project after the latest sorting integration and UI refactor work in this conversation.

## 2. Current Status

### 2.1 Cross-service flow
- `dispatch -> sorting` manifest sync is in place.
- `sorting` now owns its local manifest list and detail query.
- `sorting receive / unpack-item / route-calculate / re-bag` are available through REST.

### 2.2 Sorting workbench refactor
- `SortingRouteView` was rebuilt into a scan-driven streaming workbench.
- `SortingRebagView` was rebuilt into a slot-grid sealing console.
- Frontend build passed successfully.

### 2.3 API wiring
- `liana-web/src/lib/gatewayApi.js` includes `sorting`.
- `liana-web/vite.config.js` proxies `/liana-sorting-service`.
- `liana-web/src/lib/api.js` now includes:
  - `routeCalculateScan`
  - `listSlots`
  - `sealBagBySlot`
  - `listLinesByRoute`

### 2.4 Sorting backend additions
- `POST /api/v1/sorting/manifest/sync`
- `POST /api/v1/sorting/route-calculate/scan`
- `GET /api/v1/sorting/slots`
- `POST /api/v1/sorting/slots/seal`

### 2.5 Business stubs
- Normal scan path returns a PASS audit result.
- `RD588151316CN` triggers the DANGER stub.
- Security failures write a `SECURITY_FAILED` discrepancy record.
- The scan route writes a linear item line for traceability.

## 3. Fixes Completed

### 3.1 Sorting route page
- Removed manual destination and service form fields.
- Added autofocus scan input.
- Enter key triggers async route calculation and clears input.
- Added a streaming result table.
- Added an AI audit panel with PASS/DANGER shadow behavior.

### 3.2 Sorting rebag page
- Removed comma-separated item input.
- Added slot cards for local aggregation.
- Added per-slot seal action.
- Frontend refresh reloads slot counts.

### 3.3 Sorting service stability
- `listLines()` now supports empty `packageNo` and returns all lines for the workbench.
- Route audit text was normalized to ASCII-safe content to avoid encoding corruption.

## 4. Known Gaps
- Backend Maven compilation was not fully verified in this session because dependency resolution was blocked by the environment.
- Slot calculation is still a local heuristic, not a production-grade postal postcode model.
- Address/postcode/facility master data is still incomplete.
- Multi-hop local circulation still needs a clearer domain model than a single slot.

## 5. Architectural Judgment
- `slot` is useful as a local sorting-center temporary landing concept.
- `slot` should not be treated as a permanent global business identity.
- For mail that needs further internal circulation, the model should evolve toward:
  - postcode
  - destination facility
  - internal work cell / route zone
  - next-hop rule

## 6. Practical Conclusion
- The current sorting flow is now usable as a streaming workbench prototype.
- The next real improvement is not more UI polish, but more accurate master data and a more realistic route/slot model.
