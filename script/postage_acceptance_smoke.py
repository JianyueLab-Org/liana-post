#!/usr/bin/env python3
"""Local demo ONLY: writes test sales, simulated cash receipts and accepted mail.

python script/postage_acceptance_smoke.py --write-demo-data
Defaults: http://localhost, clerk001/admin001/sorter001, demo password 123456.
Override password with LIANA_DEMO_PASSWORD. No tokens or voucher codes are printed.
Requires an active tariff and the demo staff accounts. Leaves audit data intact.
"""
import argparse
import copy
import json
import os
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost')
    parser.add_argument('--write-demo-data', action='store_true')
    args = parser.parse_args()
    if not args.write_demo_data:
        parser.error('Explicit --write-demo-data is required: this creates demo business records.')
    base = args.base_url.rstrip('/')
    prefix = {'auth': '/liana-auth-service/api/auth', 'oms': '/liana-oms-service/api/oms',
              'postage': '/liana-postage-service/api/postage'}
    checks = []

    def call(service, path, body=None, token=None, expected=200):
        headers = {'Accept': 'application/json', 'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(base + prefix[service] + path,
            data=None if body is None else json.dumps(body).encode('utf-8'), headers=headers,
            method='GET' if body is None else 'POST')
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                status, payload = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, payload = error.code, json.load(error)
        if status != expected:
            # Never dump request bodies containing addresses/codes or login response tokens.
            raise AssertionError(f'{service} {path}: expected {expected}, got {status}: {payload.get("message", "error")}')
        if status == 200:
            assert payload.get('code') == 200, 'Business response must succeed'
        return payload.get('data')

    def passed(label):
        checks.append(label)
        print('PASS ' + label)

    password = os.environ.get('LIANA_DEMO_PASSWORD', '123456')
    clerk = call('auth', '/login', {'username': 'clerk001', 'password': password})['token']
    admin = call('auth', '/login', {'username': 'admin001', 'password': password})['token']
    sorter = call('auth', '/login', {'username': 'sorter001', 'password': password})['token']
    marker = 'acceptance-smoke-' + str(uuid.uuid4())[:8]
    mail = {'mailTypeCode': 'U', 'mailScope': 'DOMESTIC', 'serviceType': 'AIR',
        'additionalServices': [], 'weightGrams': 20, 'senderFullName': marker,
        'senderPhone': '0900123456', 'senderAddress': 'Demo Sender Road 1',
        'senderPostcode': '10001', 'senderIdType': 'NID', 'senderIdNumber': 'DEMO-ONLY',
        'recipientFullName': 'Demo Recipient', 'recipientPhone': '0900654321',
        'recipientAddress': 'Demo Recipient Road 2', 'recipientPostcode': '10002'}

    def quote(m):
        valid = call('oms', '/acceptances/validate', m, clerk)
        return call('postage', '/quotes', {'product': valid['mailTypeCode'], 'scope': valid['mailScope'],
            'mode': valid['serviceType'], 'destinationCountry': valid.get('destCountryCode') or 'LN',
            'weightGrams': valid['weightGrams'], 'additionalServices': valid['additionalServices']}, clerk)

    def body(m, q, stamps=None, traditional=None):
        return {'requestId': str(uuid.uuid4()), 'quoteId': q['quoteId'], 'mail': m,
            'stampCodes': stamps or [], 'traditionalStamps': traditional or [],
            'traditionalConfirmed': bool(traditional)}

    def buy(amount):
        sale = call('postage', '/sales', {'requestId': str(uuid.uuid4()),
            'lines': [{'faceValue': str(amount), 'quantity': 1}]}, clerk)
        sid = sale['sale']['saleId']
        receipt = {'requestId': 'cash-' + sid, 'amountReceived': sale['sale']['totalAmount']}
        paid = call('postage', '/sales/' + sid + '/confirm-cash', receipt, clerk)
        repeated = call('postage', '/sales/' + sid + '/confirm-cash', receipt, clerk)
        assert repeated['stamps'] == paid['stamps'] and len(paid['stamps']) == 1
        return paid

    def completed(value, prefix_code='UU'):
        assert value['status'] == 'COMPLETED' and value['waybillNo'].startswith(prefix_code)
        saved = call('oms', '/mails/' + value['waybillNo'], token=clerk)
        assert saved['status'] == 'CREATED' and saved['mailTypeCode'] == 'U'
        return value

    call('postage', '/acceptances', token=sorter, expected=403)
    call('postage', '/acceptances', expected=401)
    call('oms', '/mails', mail, clerk, expected=409)
    passed('signed permissions and old unpaid-create route blocked')

    q = quote(mail)
    assert Decimal(str(q['calculation']['totalAmount'])) == Decimal('1.50'), 'Demo tariff changed; review smoke expectations'
    request = body(mail, q, traditional=[{'faceValue': '1.20', 'quantity': 1}, {'faceValue': '0.80', 'quantity': 2}])
    traditional = completed(call('postage', '/acceptances', request, clerk))
    assert Decimal(str(traditional['appliedAmount'])) == Decimal('2.80')
    assert Decimal(str(traditional['excessAmount'])) == Decimal('1.30')
    summary = call('postage', '/acceptances/by-waybill/' + traditional['waybillNo'], token=clerk)
    assert 'mail' not in summary and 'stamps' not in summary
    passed('traditional face value x quantity, excess postage and safe mail summary')

    paid = buy('1.00')
    stamp = paid['stamps'][0]
    q = quote(mail)
    request = body(mail, q, [stamp['code']], [{'faceValue': '0.80', 'quantity': 1}])
    with ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: call('postage', '/acceptances', request, clerk), range(6)))
    mixed = completed(results[0])
    assert len({v['waybillNo'] for v in results}) == 1
    assert Decimal(str(mixed['excessAmount'])) == Decimal('0.30')
    lookup = call('postage', '/stamps/lookup', {'code': stamp['code']}, clerk)
    assert lookup['status'] == 'REDEEMED'
    audit = call('postage', '/sales/' + paid['sale']['saleId'], token=clerk)['audit']
    assert sum(e['eventType'] == 'STAMP_REDEEMED' for e in audit) == 1
    changed = copy.deepcopy(request)
    changed['mail']['recipientFullName'] = 'Changed Recipient'
    call('postage', '/acceptances', changed, clerk, expected=409)
    call('postage', '/stamps/' + stamp['stampId'] + '/void',
        {'requestId': str(uuid.uuid4()), 'reason': 'Demo redeemed guard'}, admin, expected=409)
    original = call('oms', '/acceptances/' + mixed['acceptanceId'] + '/complete', {}, clerk)
    cancelled = call('oms', '/acceptances/' + mixed['acceptanceId'] + '/abort', {}, clerk)
    assert original['waybillNo'] == mixed['waybillNo'] == cancelled['waybillNo']
    passed('six concurrent retries create one mail and one redemption; committed mail wins cancel')

    q = quote(mail)
    shortage = body(mail, q, traditional=[{'faceValue': '0.80', 'quantity': 1}])
    call('postage', '/acceptances', shortage, clerk, expected=409)
    deficit = buy('0.70')
    shortage['stampCodes'] = [deficit['stamps'][0]['code']]
    topup = completed(call('postage', '/acceptances', shortage, clerk))
    assert Decimal(str(topup['excessAmount'])) == Decimal('0.00')
    passed('shortage rejected then independent 0.70 cash purchase settles exact deficit')

    registered_mail = copy.deepcopy(mail)
    registered_mail['additionalServices'] = ['REGISTRATION']
    registered_q = quote(registered_mail)
    registered = completed(call('postage', '/acceptances', body(registered_mail, registered_q,
        traditional=[{'faceValue': str(registered_q['calculation']['totalAmount']), 'quantity': 1}]), clerk), 'RR')
    assert Decimal(str(registered_q['calculation']['totalAmount'])) == Decimal('4.50')
    passed('U plus registration remains U product, RR barcode and 3.00 additional fee')

    competing = buy('1.50')['stamps'][0]
    requests = [body(mail, quote(mail), [competing['code']]) for _ in range(6)]
    def compete(request):
        try:
            return completed(call('postage', '/acceptances', request, clerk))
        except AssertionError as error:
            if 'got 409:' not in str(error):
                raise
            return None
    with ThreadPoolExecutor(max_workers=6) as pool:
        outcomes = list(pool.map(compete, requests))
    assert sum(v is not None for v in outcomes) == 1
    passed('six different mail requests compete for one stamp: exactly one succeeds')

    invalid = copy.deepcopy(mail)
    invalid['weightGrams'] = 1.5
    call('oms', '/acceptances/validate', invalid, clerk, expected=400)
    closed = dict(mail, mailScope='INTERNATIONAL', destCountryCode='DE')
    call('oms', '/acceptances/validate', closed, clerk, expected=400)
    call('oms', '/acceptances/validate', mail, sorter, expected=403)
    passed('fractional weight, closed destination and non-counter role rejected')
    print(json.dumps({'marker': marker, 'checks': len(checks), 'traditionalWaybill': traditional['waybillNo'],
        'mixedWaybill': mixed['waybillNo'], 'topupWaybill': topup['waybillNo'],
        'registeredWaybill': registered['waybillNo'], 'mixedAcceptanceId': mixed['acceptanceId']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
