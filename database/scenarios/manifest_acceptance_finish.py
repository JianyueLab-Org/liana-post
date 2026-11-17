"""Resume the fixed acceptance on r2 only; never reset its business records."""
import json
import os
import subprocess
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
import manifest_acceptance as a


def sql(query):
    assert query.lstrip().upper().startswith('SELECT ')
    env = dict(os.environ, MYSQL_PWD=os.environ['LIANA_DB_PASSWORD'])
    return subprocess.check_output(['docker', 'exec', '-e', 'MYSQL_PWD',
        'liana-manifest-accept-r2-mysql-1', 'mysql', '-uroot', '-N', '-B', '-r',
        '-e', query], env=env, text=True, encoding='utf-8').strip()


def internal(service, path, payload):
    request = urllib.request.Request(
        f'http://127.0.0.1:{a.PORTS[service]}{a.PREFIX[service]}/internal/{path}',
        data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json',
                 'X-Manifest-Token': os.environ['LIANA_MANIFEST_TOKEN']})
    with urllib.request.urlopen(request, timeout=20) as response:
        result = json.load(response)
    assert result['code'] == 200, result
    return result['data']


def main(phase):
    s = a.load()
    t = a.login('admin001')
    if phase == 'hq-check':
        s.setdefault('hqCheckKeys', [a.key('HQCHECK'), a.key('HQZERO')])
        a.save(s)
        s['hqChecks'] = [a.checked(t, b['manifestNo'], 'LN-NAM-HQ', k)
                         for b, k in zip(s['hqBatches'], s['hqCheckKeys'])]
        assert s['hqChecks'][0]['receivedBagNos'] == [s['bags'][2]['bagNo']]
        assert s['hqChecks'][1]['receivedBagNos'] == []
        assert s['hqChecks'][1]['missingBagNos'] == [s['newBag']['bagNo']]
        a.save(s)
        print(json.dumps(s['hqChecks'], ensure_ascii=True))
    elif phase == 'review-close':
        targets = [(s['batches'][1], 'LN-OR-RC')]
        targets += [(b, 'LN-NAM-AIR') for b in [s['forwardBatch'], s['newBatch']]]
        targets += [(b, 'LN-NAM-HQ') for b in s['hqBatches']]
        reviews = s.setdefault('finalReviews', {})
        closes = s.setdefault('finalCloseKeys', {})
        for batch, facility in targets:
            no = batch['manifestNo']
            state = a.call('sorting', '/manifests/' + no + '/closure', token=t)
            if not state['check']:
                k = s.setdefault('finalCheckKeys', {}).setdefault(no, a.key('CHECK'))
                a.save(s)
                a.checked(t, no, facility, k)
            for d in a.call('sorting', '/discrepancies', token=t):
                if d.get('manifestNo') != no or d['status'] != 'OPEN':
                    continue
                k = reviews.setdefault(d['verificationNo'], a.key('REVIEW'))
                a.save(s)
                a.call('sorting', '/discrepancies/' + d['verificationNo'] + '/disposition',
                       dict(idempotencyKey=k, disposition='CONFIRMED_NOT_RECEIVED',
                            reason='Isolated acceptance confirms bag was not received.'), t)
            k = closes.setdefault(no, a.key('CLOSE'))
            a.save(s)
            result = a.call('sorting', '/manifests/' + no + '/close',
                            dict(requestNo=k, facilityCode=facility), t)
            print(json.dumps(result, ensure_ascii=True))
        a.save(s)
    elif phase == 'outage':
        no = s['hqBatches'][1]['manifestNo']
        s['closureDuringOutage'] = a.wait(
            lambda: a.call('sorting', '/manifests/' + no + '/closure', token=t),
            lambda x: x['status'] == 'CLOSING'
            and any(d['destination'] == 'TRANSPORT' and d['status'] == 'SENT' for d in x['deliveries'])
            and any(d['destination'] == 'DISPATCH' and d['attempts'] > 0 and d['status'] != 'SENT' for d in x['deliveries']),
            'independent closure outage')
        a.save(s)
        print(json.dumps(s['closureDuringOutage'], ensure_ascii=True))
    elif phase == 'fences':
        air = a.login('air-sorter')
        missing = s['newBag']['bagNo']
        candidates = a.call('sorting', '/forwarding/candidates?facilityCode=LN-NAM-AIR', token=air)
        assert not any(x['bagNo'] == missing for x in candidates)
        a.call('sorting', '/forwarding', dict(requestNo=a.key('FORBIDDEN'),
               previousManifestNo=s['newBatch']['manifestNo'], facilityCode='LN-NAM-AIR',
               destinationOrgCode='LN-NAM-HQ', facilityRouteCode='LN-AIR-HQ', bagNos=[missing]), air, expected=409)
        a.call('sorting', '/unpack-item', dict(stationCode='LN-NAM-AIR', packageNo=missing,
               manifestNo=s['newBatch']['manifestNo'], scanMode='PREALERT',
               idempotencyKey=a.key('FORBIDDEN'), items=[]), air, expected=409)
        a.call('sorting', '/receive', dict(manifestNo=s['hqBatches'][1]['manifestNo'],
               packageNos=[missing], stationCode='LN-NAM-HQ', receiveMode='PREALERT',
               idempotencyKey=a.key('LATE')), t, expected=409)
        s['missingFencesPassed'] = True
        a.save(s)
        print('PASS missing onward bag cannot be forwarded, opened or newly received')
    elif phase == 'final':
        batches = s['batches'] + [s['forwardBatch'], s['newBatch']] + s['hqBatches']
        s['finalClosures'] = [a.wait(
            lambda b=b: a.call('sorting', '/manifests/' + b['manifestNo'] + '/closure', token=t),
            lambda x: x['status'] == 'CLOSED', 'closure') for b in batches]
        s['finalTasks'] = []
        for task in [s['task'], s['airTask'], s['hqTask']]:
            code = task['taskCode']
            current = a.call('transport', '/tasks/' + code, token=t)
            if current['status'] != 'COMPLETED':
                a.call('transport', '/tasks/' + code + '/status', dict(status='COMPLETED'), t)
            s['finalTasks'].append(a.call('transport', '/tasks/' + code, token=t))
        a.save(s)
        print(json.dumps(dict(closures=s['finalClosures'], tasks=s['finalTasks']), ensure_ascii=True))
    elif phase == 'replay':
        before = sql('SELECT * FROM sorting_db.sorting_bag_position ORDER BY bag_no')
        dispatch_before = sql('SELECT * FROM dispatch_db.dispatch_bag ORDER BY id')
        current_before = sql('SELECT * FROM transport_db.transport_bag_current ORDER BY bag_no')
        commands = sql('SELECT payload_json FROM transport_db.transport_assignment_outbox ORDER BY created_at,request_no')
        count = 0
        for line in commands.splitlines():
            internal('dispatch', 'manifest-assignments', json.loads(line))
            count += 1
        receipts = sql("SELECT payload_json FROM sorting_db.sorting_receipt_outbox WHERE destination='DISPATCH' ORDER BY event_no")
        for line in receipts.splitlines():
            for service in ['dispatch', 'transport']:
                internal(service, 'bag-receipts', json.loads(line))
                count += 1
        assert before == sql('SELECT * FROM sorting_db.sorting_bag_position ORDER BY bag_no')
        assert dispatch_before == sql('SELECT * FROM dispatch_db.dispatch_bag ORDER BY id')
        assert current_before == sql('SELECT * FROM transport_db.transport_bag_current ORDER BY bag_no')
        s['lateReplayCount'] = count
        a.save(s)
        print('PASS', count, 'original assignment/release/receipt replays preserve current facts')
    elif phase == 'race':
        clerk = a.login('oro-clerk')
        for name, create in [
                ('raceMail', lambda: a.accept_mail(clerk, 'Acceptance race')),
                ('raceBag', lambda: a.bag(clerk, [s['raceMail']])),
                ('raceBatch', lambda: a.sealed(clerk, [s['raceBag']])),
                ('raceTask', lambda: a.call('transport', '/tasks', dict(
                    manifestNo=s['raceBatch']['manifestNo'], scheduleId=10), t))]:
            if name not in s:
                s[name] = create()
                a.save(s)
        task = a.call('transport', '/tasks/' + s['raceTask']['taskCode'], token=t)
        if task['status'] == 'ASSIGNED':
            a.move(clerk, task, [s['raceBag']], 'LN-OR-RC')
        s.setdefault('raceReceiveKey', a.key('RACERECEIVE'))
        s.setdefault('raceCheckKey', a.key('RACECHECK'))
        a.save(s)
        gate = Barrier(2)
        no = s['raceBatch']['manifestNo']
        def receive_race():
            gate.wait()
            try:
                return dict(receipt=a.receive(t, no, [s['raceBag']], 'LN-OR-RC', s['raceReceiveKey']))
            except AssertionError as error:
                assert error.args[0][2] == 409, error
                return dict(rejected=409)
        def check_race():
            gate.wait()
            return a.checked(t, no, 'LN-OR-RC', s['raceCheckKey'])
        with ThreadPoolExecutor(max_workers=2) as pool:
            received = pool.submit(receive_race)
            checked = pool.submit(check_race)
            s['raceResult'] = dict(receive=received.result(), check=checked.result())
        a.save(s)
        check = s['raceResult']['check']
        assert bool(check['receivedBagNos']) != bool(check['missingBagNos'])
        assert bool(check['receivedBagNos']) == ('receipt' in s['raceResult']['receive'])
        if check['receivedBagNos']:
            k = s.setdefault('raceUnpackKey', a.key('RACEUNPACK'))
            a.save(s)
            a.call('sorting', '/unpack-item', dict(manifestNo=no,
                   stationCode='LN-OR-RC', packageNo=s['raceBag']['bagNo'],
                   scanMode='PREALERT', idempotencyKey=k, items=[]), t)
            a.wait(lambda: a.call('sorting', '/unpack-requests/' + k, token=t),
                   lambda x: x['status'] == 'SENT', 'race opening')
        for d in a.call('sorting', '/discrepancies', token=t):
            if d.get('manifestNo') == no and d['status'] == 'OPEN':
                a.call('sorting', '/discrepancies/' + d['verificationNo'] + '/disposition',
                       dict(idempotencyKey=a.key('RACEREVIEW'), disposition='CONFIRMED_NOT_RECEIVED',
                            reason='Isolated concurrent check acceptance evidence.'), t)
        k = s.setdefault('raceCloseKey', a.key('RACECLOSE'))
        a.save(s)
        a.call('sorting', '/manifests/' + no + '/close', dict(requestNo=k, facilityCode='LN-OR-RC'), t)
        a.wait(lambda: a.call('sorting', '/manifests/' + no + '/closure', token=t),
               lambda x: x['status'] == 'CLOSED', 'race close')
        a.call('transport', '/tasks/' + task['taskCode'] + '/status', dict(status='COMPLETED'), t)
        print(json.dumps(s['raceResult'], ensure_ascii=True))
    elif phase == 'sea':
        clerk = a.login('accept-oro-clerk')
        for name, create in [
                ('seaMail', lambda: a.accept_mail(clerk, 'Acceptance sea hold')),
                ('seaBag', lambda: a.call('dispatch', '/bags', dict(
                    originFacilityCode='LN-OR-RC', routeCode='LN-OR-OUTER-RULE',
                    mailTypeCode='U', mailScope='DOMESTIC', mailNoList=[s['seaMail']]), clerk))]:
            if name not in s:
                s[name] = create()
                a.save(s)
        a.wait(lambda: a.call('dispatch', '/bags/by-id/' + str(s['seaBag']['id']), token=t),
               lambda b: b['packingSyncStatus'] == 'CONFIRMED', 'sea packing')
        if 'seaBatch' not in s:
            s['seaBatch'] = a.sealed(clerk, [s['seaBag']])
            a.save(s)
        no = s['seaBatch']['manifestNo']
        a.call('transport', '/routes', dict(routeCode='ACCEPT-SEA-LAND-INVALID',
               facilityRouteCode='LN-OR-OUTER', originFacilityCode='LN-OR-RC',
               destinationFacilityCode='LN-OR-02', transportType='LAND',
               estimatedHours=1, status='ACTIVE'), t, expected=400)
        a.call('transport', '/tasks', dict(manifestNo=no, scheduleId=10), t, expected=409)
        a.call('transport', '/tasks', dict(manifestNo=no, scheduleId=6),
               a.login('oro-clerk'), expected=403)
        schedule = next(x for x in a.call('transport', '/schedules?pageSize=100', token=t)['list']
                        if x['id'] == 6)
        schedule['status'] = 'SUSPENDED'
        a.call('transport', '/schedules/' + schedule['scheduleCode'], schedule, t, method='PUT')
        a.call('transport', '/tasks', dict(manifestNo=no, scheduleId=6), t, expected=409)
        schedule['status'] = 'ACTIVE'
        a.call('transport', '/schedules/' + schedule['scheduleCode'], schedule, t, method='PUT')
        if 'seaTask' not in s:
            s['seaTask'] = a.call('transport', '/tasks', dict(manifestNo=no, scheduleId=6), t)
            a.save(s)
        a.task_ready(t, s['seaTask'])
        a.call('dispatch', '/handoffs', dict(bagNo=s['seaBag']['bagNo'], toFacilityCode='LN-OR-02'), clerk)
        schedule['status'] = 'SUSPENDED'
        a.call('transport', '/schedules/' + schedule['scheduleCode'], schedule, t, method='PUT')
        a.call('transport', '/tasks/' + s['seaTask']['taskCode'] + '/status', dict(status='DEPARTED'), t, expected=409)
        s['seaHeldTask'] = a.call('transport', '/tasks/' + s['seaTask']['taskCode'], token=t)
        assert s['seaHeldTask']['status'] == 'ASSIGNED'
        a.save(s)
        print('PASS SEA-only network, non-origin permission and suspended sailing gates')
    elif phase == 'evidence':
        mails = [json.loads(line) for line in sql(
            "SELECT JSON_OBJECT('mailNo',waybill_no,'bagNo',bag_no,'packageId',package_id,"
            "'facility',current_facility_code,'status',status) FROM oms_db.mail ORDER BY waybill_no").splitlines()]
        by_no = {m['mailNo']: m for m in mails}
        assert by_no[s['mails'][0]]['bagNo'] == s['newBag']['bagNo']
        assert by_no[s['mails'][1]]['bagNo'] == s['bags'][0]['bagNo']
        for no in s['mails'][2:4]:
            assert by_no[no]['bagNo'] == s['bags'][1]['bagNo']
        for no in s['mails'][4:6]:
            assert by_no[no]['bagNo'] is None and by_no[no]['facility'] == 'LN-NAM-HQ'
        assert by_no[s['surplusMail']]['bagNo'] is None
        assert sql("SELECT COUNT(*) FROM sorting_db.sorting_manifest WHERE manifest_status='CLOSED'") == '7'
        assert sql("SELECT COUNT(*) FROM sorting_db.sorting_manifest WHERE manifest_status='CLOSING'") == '0'
        assert sql("SELECT COUNT(*) FROM transport_db.transport_manifest_current") == '1'
        assert sql("SELECT COUNT(*) FROM transport_db.transport_bag_current") == '1'
        assert sql("SELECT COUNT(*) FROM sorting_db.sorting_manifest_package p JOIN sorting_db.sorting_bag_receipt r ON r.manifest_no=p.manifest_no AND r.bag_no=p.package_no WHERE p.item_status='MISSING'") == '0'
        assert sql("SELECT COUNT(*) FROM transport_db.transport_manifest_assignment WHERE status='SETTLED' AND received_at IS NOT NULL") == '0'
        positions = sql('SELECT * FROM sorting_db.sorting_bag_position ORDER BY bag_no')
        s['finalDatabaseEvidence'] = dict(mails=mails, positions=positions,
            currentReservations='one manifest and one bag: suspended SEA task only')
        a.save(s)
        print(json.dumps(s['finalDatabaseEvidence'], ensure_ascii=True))
    else:
        raise ValueError('Unknown phase')


if __name__ == '__main__':
    main(sys.argv[1])
