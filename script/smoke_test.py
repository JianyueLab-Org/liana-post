#!/usr/bin/env python3
"""Service interaction smoke test helper.

Usage examples:
  python script/smoke_test.py --base-url http://127.0.0.1
  python script/smoke_test.py --oms 8083 --dispatch 8084 --tracking 8085 --facility 8082

The script follows SERVICE_INTERACTION_SMOKE_TEST.md:
- connectivity checks
- daytime mail creation + accepted tracking
- dispatch candidate query
- night bag creation + sync + bag??
- arrival status update + tracking
- query verification
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple


@dataclass(frozen=True)
class ServiceEndpoint:
    name: str
    base_url: str


class HttpError(RuntimeError):
    pass


def build_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def request_json(method: str, url: str, payload: Optional[dict] = None, timeout: float = 10.0) -> Tuple[int, Any, str]:
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            status = resp.getcode()
            try:
                parsed = json.loads(body) if body else None
            except json.JSONDecodeError:
                parsed = body
            return status, parsed, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise HttpError(f"HTTP {exc.code} for {url}: {body}") from exc
    except urllib.error.URLError as exc:
        raise HttpError(f"Request failed for {url}: {exc}") from exc


def unwrap_result(response: Any) -> Any:
    if isinstance(response, dict) and "data" in response:
        return response.get("data")
    return response


def print_step(title: str) -> None:
    print(f"\n=== {title} ===")


def ensure_ok(response: Any, label: str) -> Any:
    if not isinstance(response, dict):
        return response
    code = response.get("code")
    if code not in (200, None):
        raise HttpError(f"{label} failed: {response}")
    return unwrap_result(response)


def find_first(items: Any, key: str = None) -> Optional[dict]:
    if not isinstance(items, list) or not items:
        return None
    for item in items:
        if isinstance(item, dict):
            if key is None:
                return item
            value = item.get(key)
            if value:
                return item
    return items[0] if isinstance(items[0], dict) else None


def extract_value(item: Any, *keys: str) -> Optional[str]:
    if not isinstance(item, dict):
        return None
    for key in keys:
        value = item.get(key)
        if value:
            return value
    return None


def get_service(base: str, port: int) -> ServiceEndpoint:
    return ServiceEndpoint(name=str(port), base_url=f"{base}:{port}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Service interaction smoke test helper")
    parser.add_argument("--base-url", default="http://127.0.0.1", help="Base host for all services")
    parser.add_argument("--facility", type=int, default=8084)
    parser.add_argument("--oms", type=int, default=8083)
    parser.add_argument("--dispatch", type=int, default=8082)
    parser.add_argument("--tracking", type=int, default=8085)
    parser.add_argument("--waybill-no", default="")
    parser.add_argument("--bag-no", default="")
    args = parser.parse_args()

    facility = get_service(args.base_url, args.facility)
    oms = get_service(args.base_url, args.oms)
    dispatch = get_service(args.base_url, args.dispatch)
    tracking = get_service(args.base_url, args.tracking)

    try:
        print_step("1) Connectivity checks")
        checks = [
            ("facility types", "GET", build_url(facility.base_url, "/api/facilities/types"), None),
            ("facility list", "GET", build_url(facility.base_url, "/api/facilities"), None),
            ("facility routes", "GET", build_url(facility.base_url, "/api/facilities/routes"), None),
            ("oms mails", "GET", build_url(oms.base_url, "/api/oms/mails"), None),
            ("dispatch bags", "GET", build_url(dispatch.base_url, "/api/dispatch/bags"), None),
            ("tracking events", "GET", build_url(tracking.base_url, "/api/tracking/events"), None),
        ]
        for label, method, url, payload in checks:
            status, parsed, _ = request_json(method, url, payload)
            print(f"[OK] {label}: HTTP {status}")

        print_step("2) Daytime mail creation")
        existing_mail = None
        if args.waybill_no:
            status, parsed, _ = request_json("GET", build_url(oms.base_url, f"/api/oms/mails/{urllib.parse.quote(args.waybill_no)}"))
            if status == 200:
                existing_mail = ensure_ok(parsed, "existing mail lookup")
        create_mail_payload = {
            "senderFullName": "Smoke Sender",
            "senderPhone": "13800000000",
            "senderIdType": "ID",
            "senderIdNumber": "S1234567890",
            "senderAddress": "Smoke Road 1",
            "senderPostcode": "100000",
            "recipientFullName": "Smoke Recipient",
            "recipientPhone": "13900000000",
            "recipientAddress": "Smoke Avenue 2",
            "recipientPostcode": "100000",
            "mailTypeCode": "C",
            "serviceType": "AIR",
            "originFacilityCode": "B1",
            "currentFacilityCode": "B1",
            "destFacilityCode": "C1",
            "weightGrams": 100,
            "declaredValue": 0,
        }
        if existing_mail:
            waybill_no = extract_value(existing_mail, "waybillNo")
            print(f"[OK] reused mail waybillNo={waybill_no}")
        else:
            raise HttpError("New mail requires postage settlement. Run script/postage_acceptance_smoke.py first, then pass --waybill-no for an existing mail to this logistics script.")
        if not waybill_no:
            raise HttpError("cannot determine waybillNo from OMS create response")

        status, parsed, _ = request_json("POST", build_url(tracking.base_url, "/api/tracking/events"), {
            "waybillNo": waybill_no,
            "eventType": "ACCEPTED",
            "facilityCode": "B1",
            "facilityName": "Smoke Facility B1",
            "operatorId": 1001,
            "operatorName": "Smoke Operator",
            "payload": json.dumps({"source": "smoke"}, ensure_ascii=False),
            "sourceService": "oms"
        })
        ensure_ok(parsed, "tracking accepted event")
        print("[OK] tracking ACCEPTED recorded")

        print_step("3) Dispatch candidate query")
        status, parsed, _ = request_json("POST", build_url(oms.base_url, "/api/oms/mails/dispatch-candidates"), {
            "currentFacilityCode": "B1",
            "destFacilityCode": "C1",
            "mailTypeCode": "C"
        })
        candidates = ensure_ok(parsed, "dispatch candidates")
        print(f"[OK] candidates size={len(candidates) if isinstance(candidates, list) else 'n/a'}")

        print_step("4) Night bag creation and sync")
        mail_list = [waybill_no]
        existing_bag = None
        if args.bag_no:
            status, parsed, _ = request_json("GET", build_url(dispatch.base_url, "/api/dispatch/bags"))
            bag_candidates = ensure_ok(parsed, "list dispatch bags")
            existing_bag = next((item for item in bag_candidates if isinstance(item, dict) and item.get("bagNo") == args.bag_no), None)
        create_bag_payload = {
            "originFacilityCode": "B1",
            "destinationFacilityCode": "C1",
            "routeCode": "R-B1-C1",
            "mailTypeCode": "C",
            "mailNoList": mail_list,
        }
        if existing_bag:
            bag = existing_bag
            bag_no = extract_value(bag, "bagNo")
            print(f"[OK] reused bag bagNo={bag_no}")
        else:
            status, parsed, _ = request_json("POST", build_url(dispatch.base_url, "/api/dispatch/bags"), create_bag_payload)
            bag = ensure_ok(parsed, "create dispatch bag")
            bag_no = args.bag_no or extract_value(bag, "bagNo")
            print(f"[OK] created bag bagNo={bag_no}")
        if not bag_no:
            raise HttpError("cannot determine bagNo from Dispatch create response")

        status, parsed, _ = request_json("POST", build_url(dispatch.base_url, "/api/dispatch/bags/sync-mail"), {
            "bagNo": bag_no,
            "currentFacilityCode": "B1",
            "mailNoList": mail_list,
        })
        ensure_ok(parsed, "dispatch sync-mail")
        print("[OK] dispatch sync-mail completed")

        status, parsed, _ = request_json("POST", build_url(oms.base_url, f"/api/oms/mails/{urllib.parse.quote(waybill_no)}/bag"), {
            "bagNo": bag_no,
            "currentFacilityCode": "B1",
        })
        ensure_ok(parsed, "oms bag assign")
        print("[OK] oms bag?? completed")

        status, parsed, _ = request_json("POST", build_url(tracking.base_url, "/api/tracking/events"), {
            "waybillNo": waybill_no,
            "eventType": "DISPATCHED",
            "facilityCode": "B1",
            "facilityName": "Smoke Facility B1",
            "operatorId": 1001,
            "operatorName": "Smoke Operator",
            "payload": json.dumps({"bagNo": bag_no}, ensure_ascii=False),
            "sourceService": "dispatch"
        })
        ensure_ok(parsed, "tracking dispatched event")
        print("[OK] tracking DISPATCHED recorded")

        print_step("5) Arrival flow and query verification")
        status, parsed, _ = request_json("POST", build_url(oms.base_url, f"/api/oms/mails/{urllib.parse.quote(waybill_no)}/status"), {
            "status": "ARRIVED",
            "currentFacilityCode": "C1",
        })
        ensure_ok(parsed, "oms update status")
        print("[OK] oms status updated to ARRIVED")

        status, parsed, _ = request_json("POST", build_url(tracking.base_url, "/api/tracking/events"), {
            "waybillNo": waybill_no,
            "eventType": "ARRIVED",
            "facilityCode": "C1",
            "facilityName": "Smoke Facility C1",
            "operatorId": 1001,
            "operatorName": "Smoke Operator",
            "payload": json.dumps({"bagNo": bag_no}, ensure_ascii=False),
            "sourceService": "oms"
        })
        ensure_ok(parsed, "tracking arrived event")
        print("[OK] tracking ARRIVED recorded")

        status, parsed, _ = request_json("GET", build_url(tracking.base_url, f"/api/tracking/events/waybill/{urllib.parse.quote(waybill_no)}"))
        events = ensure_ok(parsed, "tracking query by waybill")
        print(f"[OK] tracking events count={len(events) if isinstance(events, list) else 'n/a'}")

        first_event_no = None
        if isinstance(events, list) and events:
            first_event = find_first(events)
            first_event_no = extract_value(first_event, "eventNo")
        if not first_event_no:
            raise HttpError("cannot determine eventNo from tracking query response")

        status, parsed, _ = request_json("GET", build_url(tracking.base_url, f"/api/tracking/events/{urllib.parse.quote(first_event_no)}"))
        ensure_ok(parsed, "tracking event-by-no query")
        print("[OK] tracking event-by-no query completed")

        print_step("6) Redis observation hints")
        print("[INFO] Repeat the same tracking query twice and compare latency manually.")
        print("[INFO] If you need cache verification, inspect service logs / Redis keys.")

        print("\nALL SMOKE STEPS COMPLETED")
        return 0

    except HttpError as exc:
        print(f"\n[FAIL] {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"\n[FAIL] unexpected error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
