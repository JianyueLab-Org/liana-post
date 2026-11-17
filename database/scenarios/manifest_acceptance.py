"""Fixed HTTP acceptance helpers. Only the dedicated localhost ports are allowed.

Business records remain in the isolated database. Credentials come from environment.
Run individual scenarios from a Python driver importing this module.
"""
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

PORTS = dict(auth=18081, facility=18082, oms=18083, dispatch=18084,
             tracking=18085, transport=18086, sorting=18087, postage=18090)
PREFIX = dict(auth='/api/auth', facility='/api/facilities', oms='/api/oms',
              dispatch='/api/dispatch', tracking='/api/tracking',
              transport='/api/transport', sorting='/api/v1/sorting', postage='/api/postage')
STATE = Path(os.environ['TEMP']) / 'liana-manifest-accept-r2' / 'scenario.json'


def call(service, path='', body=None, token=None, expected=200, method=None):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(
        f'http://127.0.0.1:{PORTS[service]}{PREFIX[service]}{path}',
        data=None if body is None else json.dumps(body).encode('utf-8'),
        headers=headers, method=method or ('GET' if body is None else 'POST'))
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status, payload = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, payload = error.code, json.load(error)
    actual = payload.get('code', status) if status == 200 else status
    assert actual == expected, (service, path, status, payload.get('code'), payload.get('message'))
    if expected == 200:
        assert payload.get('code') == 200, (service, path, payload.get('message'))
    return payload.get('data')


def login(username):
    return call('auth', '/login', {'username': username,
                'password': os.environ['LIANA_DEMO_PASSWORD']})['token']


def wait(read, predicate, label, timeout=50):
    deadline = time.monotonic() + timeout
    while True:
        value = read()
        if predicate(value):
            return value
        if time.monotonic() >= deadline:
            raise AssertionError((label, value))
        time.sleep(2)


def load():
    return json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}


def save(state):
    STATE.write_text(json.dumps(state, indent=2), encoding='utf-8', newline='\n')


def key(label):
    return 'ACCEPT-' + label + '-' + uuid.uuid4().hex[:16]


def accept_mail(token, marker):
    mail = dict(mailTypeCode='U', mailScope='DOMESTIC', serviceType='AIR',
                additionalServices=[], weightGrams=20, senderFullName=marker,
                senderPhone='0900123456', senderAddress='Oro Demo Road 1',
                senderPostcode='2001', senderIdType='NID', senderIdNumber='DEMO-ONLY',
                recipientFullName='Fictional Recipient', recipientPhone='0900654321',
                recipientAddress='Namoa Demo Road 2', recipientPostcode='1001')
    valid = call('oms', '/acceptances/validate', mail, token)
    quote = call('postage', '/quotes', dict(product=valid['mailTypeCode'],
                 scope=valid['mailScope'], mode=valid['serviceType'], destinationCountry='LN',
                 weightGrams=valid['weightGrams'], additionalServices=[]), token)
    request = dict(requestId=key('MAIL'), quoteId=quote['quoteId'], mail=mail,
                   stampCodes=[], traditionalStamps=[dict(
                       faceValue=str(quote['calculation']['totalAmount']), quantity=1)],
                   traditionalConfirmed=True)
    result = call('postage', '/acceptances', request, token)
    assert result['status'] == 'COMPLETED', result
    assert call('postage', '/acceptances', request, token)['waybillNo'] == result['waybillNo']
    return result['waybillNo']


def bag(token, mails):
    result = call('dispatch', '/bags', dict(originFacilityCode='LN-OR-01',
                  routeCode='LN-OR-01-RC-RULE',
                  mailTypeCode='U', mailScope='DOMESTIC', mailNoList=mails), token)
    return wait(lambda: call('dispatch', '/bags/by-id/' + str(result['id']), token=token),
                lambda b: b['packingSyncStatus'] == 'CONFIRMED', 'OMS packing')


def sealed(token, bags):
    result = call('dispatch', '/batches', dict(bagNos=[b['bagNo'] for b in bags]), token)
    return seal_existing(token, result)


def seal_existing(token, result):
    def current():
        return next(b for b in call('dispatch', '/batches', token=token)
                    if b['batchNo'] == result['batchNo'])
    wait(current, lambda b: b['manifestSyncStatus'] == 'SENT', 'prealert')
    call('dispatch', '/batches/' + result['batchNo'] + '/seal', {}, token)
    return wait(current, lambda b: b['manifestSealStatus'] == 'SENT', 'seal')


def forward(token, manifest, bag, source, destination, route):
    wait(lambda: call('sorting', '/forwarding/candidates?facilityCode=' + source, token=token),
         lambda xs: any(x['bagNo'] == bag['bagNo'] for x in xs), 'dual receipt confirmation')
    request = dict(requestNo=key('FORWARD'), previousManifestNo=manifest,
                   facilityCode=source, destinationOrgCode=destination,
                   facilityRouteCode=route, bagNos=[bag['bagNo']])
    state = call('sorting', '/forwarding', request, token)
    def confirmed():
        return next(x for x in call('sorting', '/forwarding?facilityCode=' + source, token=token)
                    if x['command']['requestNo'] == state['command']['requestNo'])
    state = wait(confirmed, lambda x: x['status'] == 'SENT', 'forwarding')
    batches = call('dispatch', '/batches', token=token)
    batch = next(x for x in batches if x['manifestNo'] == state['command']['manifestNo'])
    return seal_existing(token, batch), request


def task_ready(token, task):
    return wait(lambda: call('transport', '/tasks/' + task['taskCode'], token=token),
                lambda t: all(a['status'] == 'CONFIRMED' for a in t['manifestAssignments']),
                'assignment confirmation')


def move(token, task, bags, destination):
    task_ready(token, task)
    for b in bags:
        call('dispatch', '/handoffs', dict(bagNo=b['bagNo'], toFacilityCode=destination), token)
    for status in ['DEPARTED', 'ARRIVED']:
        call('transport', '/tasks/' + task['taskCode'] + '/status', dict(status=status), token)


def receive(token, manifest, bags, station, request_no):
    return call('sorting', '/receive', dict(manifestNo=manifest, stationCode=station,
                packageNos=[b['bagNo'] for b in bags], receiveMode='PREALERT',
                idempotencyKey=request_no), token)


def checked(token, manifest, station, request_no):
    return call('sorting', '/manifests/' + manifest + '/finish-check',
                dict(requestNo=request_no, facilityCode=station), token)


if __name__ == '__main__':
    admin = login('admin001')
    assert len(call('facility', token=admin)) == 15
    assert call('oms', '/mails', token=admin) == []
    for service in ['dispatch', 'sorting', 'transport']:
        path = dict(dispatch='/bags', sorting='/manifests', transport='/tasks')[service]
        result = call(service, path, token=admin)
        assert (result['list'] if isinstance(result, dict) else result) == []
    print('PASS isolated login, 15 facilities and empty business tables')
