"""Exercise the deployed demo through its web gateway, never direct database writes."""
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / '.dist' / 'demo-smoke.json'
PREFIX = dict(auth='liana-auth-service/api/auth', oms='liana-oms-service/api/oms',
              facility='liana-facility-service/api/facilities',
              postage='liana-postage-service/api/postage', dispatch='liana-dispatch-service/api/dispatch',
              sorting='liana-sorting-service/api/v1/sorting', transport='liana-transport-service/api/transport')


def environment():
    result = dict(os.environ)
    for line in (ROOT / '.env.demo').read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            name, value = line.split('=', 1)
            result.setdefault(name.strip(), value.strip())
    return result


def main():
    env = environment()
    base = 'http://127.0.0.1:' + env.get('LIANA_WEB_PORT', '80')

    def call(service, path='', body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        for attempt in range(15):
            request = urllib.request.Request(base + '/' + PREFIX[service] + path,
                data=None if body is None else json.dumps(body).encode('utf-8'), headers=headers)
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    result = json.load(response)
                assert result['code'] == 200, (service, path, result.get('message'))
                return result['data']
            except urllib.error.HTTPError as error:
                if error.code != 429 or attempt == 14:
                    raise
                time.sleep(1)

    def wait(read, predicate):
        for _ in range(40):
            value = read()
            if predicate(value):
                return value
            time.sleep(2)
        raise AssertionError(value)

    def login(user):
        return call('auth', '/login', dict(username=user, password=env['LIANA_DEMO_PASSWORD']))['token']

    admin, clerk, sorter = [login(u) for u in ['admin001', 'oro-clerk', 'oro-sorter']]
    assert len(call('facility', token=admin)) == 15
    s = json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}

    def save():
        STATE.parent.mkdir(parents=True, exist_ok=True)
        STATE.write_text(json.dumps(s, indent=2), encoding='utf-8', newline='\n')

    def fixed(name):
        if name not in s:
            s[name] = 'DEMO-' + uuid.uuid4().hex[:24]
            save()
        return s[name]

    if not s.get('mails'):
        s['mails'] = []
        save()
    while len(s['mails']) < 2:
        mail = dict(mailTypeCode='U', mailScope='DOMESTIC', serviceType='AIR', additionalServices=[],
            weightGrams=20, senderFullName='Fictional Demo Sender', senderPhone='0900123456',
            senderAddress='Oro Demo Road 1', senderPostcode='2001', senderIdType='NID',
            senderIdNumber='DEMO-ONLY', recipientFullName='Fictional Demo Recipient',
            recipientPhone='0900654321', recipientAddress='Namoa Demo Road 2', recipientPostcode='1001')
        call('oms', '/acceptances/validate', mail, clerk)
        quote = call('postage', '/quotes', dict(product='U', scope='DOMESTIC', mode='AIR',
            destinationCountry='LN', weightGrams=20, additionalServices=[]), clerk)
        request = dict(requestId=fixed('mailKey' + str(len(s['mails']))), quoteId=quote['quoteId'], mail=mail,
            stampCodes=[], traditionalStamps=[dict(faceValue=str(quote['calculation']['totalAmount']), quantity=1)],
            traditionalConfirmed=True)
        accepted = call('postage', '/acceptances', request, clerk)
        assert accepted['status'] == 'COMPLETED'
        assert call('postage', '/acceptances', request, clerk)['waybillNo'] == accepted['waybillNo']
        s['mails'].append(accepted['waybillNo'])
        save()
    if 'bag' not in s:
        s['bag'] = call('dispatch', '/bags', dict(originFacilityCode='LN-OR-01',
            routeCode='LN-OR-01-RC-RULE', mailTypeCode='U', mailScope='DOMESTIC', mailNoList=s['mails']), clerk)
        save()
    bag = s['bag']
    wait(lambda: call('dispatch', '/bags/by-id/' + str(bag['id']), token=clerk),
         lambda b: b['packingSyncStatus'] == 'CONFIRMED')
    if 'batch' not in s:
        s['batch'] = call('dispatch', '/batches', dict(bagNos=[bag['bagNo']]), clerk)
        save()
    batch = s['batch']
    no = batch['manifestNo']
    if not s.get('completed'):
        def current_batch():
            return next(b for b in call('dispatch', '/batches', token=clerk) if b['batchNo'] == batch['batchNo'])
        wait(current_batch, lambda b: b['manifestSyncStatus'] == 'SENT')
        if current_batch()['manifestSealStatus'] == 'NOT_REQUESTED':
            call('dispatch', '/batches/' + batch['batchNo'] + '/seal', {}, clerk)
        wait(current_batch, lambda b: b['manifestSealStatus'] == 'SENT')
        if 'task' not in s:
            s['task'] = call('transport', '/tasks', dict(manifestNo=no, scheduleId=10), clerk)
            save()
        code = s['task']['taskCode']
        task = wait(lambda: call('transport', '/tasks/' + code, token=clerk),
                    lambda t: all(a['status'] in ['CONFIRMED', 'RECEIVED', 'SETTLED'] for a in t['manifestAssignments']))
        if task['status'] == 'ASSIGNED':
            call('dispatch', '/handoffs', dict(bagNo=bag['bagNo'], toFacilityCode='LN-OR-RC'), clerk)
            call('transport', '/tasks/' + code + '/status', dict(status='DEPARTED'), clerk)
        if call('transport', '/tasks/' + code, token=clerk)['status'] == 'DEPARTED':
            call('transport', '/tasks/' + code + '/status', dict(status='ARRIVED'), clerk)
        call('sorting', '/receive', dict(manifestNo=no, stationCode='LN-OR-RC',
            packageNos=[bag['bagNo']], receiveMode='PREALERT', idempotencyKey=fixed('receiveKey')), sorter)
        unpack = dict(manifestNo=no, stationCode='LN-OR-RC', packageNo=bag['bagNo'], scanMode='PREALERT',
            idempotencyKey=fixed('unpackKey'), items=[dict(itemNo=m) for m in s['mails']])
        call('sorting', '/unpack-item', unpack, sorter)
        wait(lambda: call('sorting', '/unpack-requests/' + s['unpackKey'], token=sorter), lambda r: r['status'] == 'SENT')
        call('sorting', '/manifests/' + no + '/finish-check',
             dict(requestNo=fixed('checkKey'), facilityCode='LN-OR-RC'), sorter)
        wait(lambda: call('sorting', '/manifests/' + no + '/bag-receipts', token=sorter),
             lambda rows: len(rows) == 2 and all(r['status'] == 'SENT' for r in rows))
        call('sorting', '/manifests/' + no + '/close', dict(requestNo=fixed('closeKey'), facilityCode='LN-OR-RC'), admin)
        wait(lambda: call('sorting', '/manifests/' + no + '/closure', token=admin), lambda r: r['status'] == 'CLOSED')
        call('transport', '/tasks/' + code + '/status', dict(status='COMPLETED'), clerk)
        s['completed'] = True
        save()
    assert call('sorting', '/manifests/' + no + '/closure', token=admin)['status'] == 'CLOSED'
    assert call('transport', '/tasks/' + s['task']['taskCode'], token=admin)['status'] == 'COMPLETED'
    print(json.dumps(dict(result='PASS deployed gateway acceptance', mails=s['mails'],
                          bagNo=bag['bagNo'], manifestNo=no, taskCode=s['task']['taskCode'])))


if __name__ == '__main__':
    main()
