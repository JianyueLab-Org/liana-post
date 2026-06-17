# Sorting Supplement Development and Next Steps

## 1. This Round Delivered

### 1.1 Frontend
- Rebuilt `SortingRouteView` into a streaming scan workbench.
- Rebuilt `SortingRebagView` into a slot-grid sealing board.
- Removed the old manual form style from both pages.
- Added scan-first interaction:
  - autofocus input
  - Enter to submit
  - immediate clear
  - keep focus
- Added AI audit shadow behavior:
  - normal scan => PASS
  - `RD588151316CN` => DANGER stub

### 1.2 Backend
- Added scan-based route calculation endpoint.
- Added slot summary query endpoint.
- Added slot seal endpoint.
- Added security-failure discrepancy write path.
- Added empty-parameter fallback for line listing.

### 1.3 Integration
- Frontend `sorting` service routing is wired.
- Vite proxy for `/liana-sorting-service` is present.
- Sorting local manifest access is available.

## 2. What This Round Actually Solved
- The sorting UI is no longer a dead static admin form.
- The route page now behaves like a scan-driven workbench.
- The rebag page now behaves like a local slot collector.
- The sorting side can now show a usable streaming prototype for center-side work.

## 3. What Still Needs Work

### 3.1 Master data
- Real address -> postcode mapping
- Postcode -> facility mapping
- Facility hierarchy and area coverage
- More realistic destination routing rules

### 3.2 Slot model
- Slot should remain a local sorting-center temporary concept.
- Slot should not become the permanent identity of a mail item.
- Multi-hop internal circulation should use a richer model than a single slot.

### 3.3 Sorting domain
- More realistic internal work cell / route zone modeling
- Better support for mail that needs further local circulation
- Better traceability from scan -> route -> seal -> dispatch

## 4. Next-Step Recommendation

### Phase 1
- Expand postcode and facility master data.
- Replace the current slot heuristic with a clearer postcode slicing rule.
- Add a facility / route mapping table if needed.

### Phase 2
- Introduce internal circulation levels:
  - route zone
  - work cell
  - next hop
- Keep slot only as the temporary load/unload landing point.

### Phase 3
- Make the workbench data come from real routing rules instead of local heuristics.
- Add more realistic exception flows and more accurate discrepancy records.

## 5. Bottom Line
- Yes, the next direction is to refine postcode and slot.
- But the slot should be constrained to local sorting operations.
- The real long-term model should be postcode + facility + internal route zone, not slot alone.
