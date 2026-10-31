"""Executable evidence/reporting rules. Validation is not a claim that evidence was collected."""
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import re
from urllib.parse import urlparse

MANIFEST = Path(__file__).with_name('programme.json')
STATUSES = {'passed', 'failed', 'skipped', 'unknown', 'provisional'}


def manifest():
    d = json.loads(MANIFEST.read_text())
    ids = [c['id'] for c in d['categories']]
    batch_ids = [i for b in d['batches'] for i in b['categories']]
    if d.get('schema') != 1 or ids != list(range(1,25)) or sorted(batch_ids) != ids:
        raise ValueError('INVALID_PROGRAMME_MANIFEST')
    # Reviewed production component tests are evidence for a category, but
    # cannot establish end-to-end, real-road or release acceptance.
    components=d.get('component_category_map')
    if not isinstance(components,dict) or any(
            not isinstance(name,str)
            or not re.fullmatch(r'com\.drivemate\.app\.[A-Za-z0-9_.]+Test',name)
            or type(category) is not int or category not in ids
            for name,category in components.items()):
        raise ValueError('INVALID_COMPONENT_CATEGORY_MAP')
    return d


def counts(tests):
    n = Counter(t['status'] for t in tests)
    if set(n) - STATUSES:
        raise ValueError('INVALID_RESULT_STATUS')
    status = ('not_tested' if not tests else 'failed' if n['failed'] else
              'passed' if n['passed'] == len(tests) else 'incomplete')
    return {'correct': n['passed'], 'total': len(tests), 'failed': n['failed'],
            'unknown': n['unknown'], 'provisional': n['provisional'], 'skipped': n['skipped'],
            'status': status, 'reason_code': 'NO_RECORDED_CHECKS' if not tests else
            'CHECK_FAILED' if n['failed'] else 'INCOMPLETE_EVIDENCE' if status == 'incomplete' else 'CHECKS_PASSED'}


def programme_summary(doc):
    m = manifest()
    groups = {c['id']: [] for c in m['categories']}
    unassigned = []
    for item, group in doc['checklist'].items():
        if item == 'App unit tests':
            # Classify only explicitly reviewed existing components. This
            # partitions the observed tests; it never duplicates a count.
            for test in group['tests']:
                current=m['component_category_map'].get(test.get('class'))
                (groups[current] if current else unassigned).append(test)
            continue
        old = re.match(r'^(?:cat)?(\d+)(?: |:)', item)
        current = int(old.group(1)) if old and 1 <= int(old.group(1)) <= len(m['categories']) else None
        (groups[current] if current else unassigned).extend(group['tests'])
    categories = [dict(c, **counts(groups[c['id']])) for c in m['categories']]
    # Category 1 has only component evidence here; its overall acceptance
    # remains open even when all mapped unit tests pass.
    categories[0]['acceptance'] = 'open'
    categories[0]['evidence_scope'] = 'component_checks_only'
    return {'schema': 1, 'app_sha': doc['app_commit'], 'test_sha': doc['public_tests_sha'],
            'categories': categories,
            'unassigned': counts(unassigned), 'batches': m['batches'],
            'scope': 'Automated checks only; a passing component is not source-to-screen or real-drive acceptance'}


def immutable(e):
    for key, length in [('app_sha',40), ('test_sha',40), ('map_sha256',64), ('input_sha256',64)]:
        if not isinstance(e.get(key), str) or not re.fullmatch('[0-9a-f]{'+str(length)+'}',e[key]):
            raise ValueError('INVALID_IMMUTABLE_ID:' + key)


def independent_truth(truth):
    if truth.get('status') != 'verified':
        raise ValueError('INDEPENDENT_TRUTH_REQUIRED')
    host = urlparse(truth.get('url','')).hostname or ''
    # Evidence from an engine or an OSM tag alone is not independent UK legal truth.
    if not (host.endswith('.gov.uk') or host == 'gov.uk' or host == 'nationalhighways.co.uk' or host.endswith('.nationalhighways.co.uk')):
        raise ValueError('INDEPENDENT_TRUTH_REQUIRED:official_source')
    if not re.fullmatch('[0-9a-f]{64}',truth.get('document_sha256','')):
        raise ValueError('INDEPENDENT_TRUTH_REQUIRED:document_digest')
    try:
        date = datetime.fromisoformat(truth['observed_utc'].replace('Z','+00:00'))
        if date.tzinfo is None or date > datetime.now(timezone.utc):
            raise ValueError()
    except (KeyError, TypeError, ValueError):
        raise ValueError('INDEPENDENT_TRUTH_REQUIRED:dated_evidence') from None


def validate_offline_proof(e):
    """Validate *claimed* offline delivery evidence, not independent CI attestation.

    A valid ledger record remains insufficient to prove that an emulator
    actually had its network isolated; CI must collect and cross-check the
    referenced probe, published release and readback artifacts.
    """
    proof=e.get('offline_proof')
    if not isinstance(proof,dict):
        raise ValueError('OFFLINE_PROOF_REQUIRED')
    network=proof.get('network')
    if (not isinstance(network,dict)
        or network.get('method') not in {'emulator_network_namespace','adb_with_reachability_probe'}
        or network.get('blocked_before') is not True
        or network.get('blocked_after') is not True
        or not isinstance(network.get('probe_sha256'),str)
        or not re.fullmatch(r'[0-9a-f]{64}',network['probe_sha256'])):
        raise ValueError('OFFLINE_NETWORK_NOT_VERIFIED')
    delivery=proof.get('delivery')
    if (not isinstance(delivery,dict)
        or delivery.get('source') != 'published_release'
        or delivery.get('install_verified_before_isolation') is not True
        or delivery.get('read_after_isolation') is not True
        or any(not isinstance(delivery.get(key),str)
               or not re.fullmatch(r'[0-9a-f]{64}',delivery[key])
               for key in ('catalog_sha256','manifest_sha256','installed_sha256','read_sha256'))
        or delivery.get('installed_sha256') != e['input_sha256']
        or delivery.get('read_sha256') != e['input_sha256']):
        raise ValueError('OFFLINE_ASSET_NOT_VERIFIED')


def validate_acceptance(e):
    immutable(e)
    if e.get('country') not in {'GB','UK','GB-NIR'} or e.get('source_kind') != 'published_real_uk' or not e.get('case_id'):
        raise ValueError('REAL_UK_SOURCE_REQUIRED')
    if e.get('mode') not in {'online','offline','controlled_cache'}:
        raise ValueError('MODE_REQUIRED')
    for stage in ('source','loader','session','screen'):
        row = e.get('stages',{}).get(stage,{})
        if row.get('status') != 'passed' or not re.fullmatch('[0-9a-f]{64}',row.get('artifact_sha256','')):
            raise ValueError('MISSING_ACCEPTANCE_STAGE:' + stage)
    scope='pipeline_only'
    if e.get('mode') == 'offline':
        validate_offline_proof(e)
        scope='offline_evidence_record_valid'
    if e.get('claim') in {'legal_lane_advice','legal_manoeuvre','speed_limit_legality'}:
        independent_truth(e.get('legal_truth',{}))
        return {'scope':'dated_official_truth_attached','status':'evidence_record_valid'}
    return {'scope':scope,'status':'evidence_record_valid'}


def validate_retest(before,after):
    immutable(before); immutable(after)
    for k in ('test_sha','map_sha256','input_sha256','case_id','mode','country'):
        if before.get(k) != after.get(k):
            raise ValueError('RETEST_INPUT_DRIFT:' + k)


def validate_benchmark(entries):
    if not entries:
        raise ValueError('EMPTY_BENCHMARK')
    anchor = entries[0]
    results=[]
    seen_engines=set()
    for e in entries:
        engine=e.get('engine')
        if not isinstance(engine,str) or not engine.strip():
            raise ValueError('BENCHMARK_ENGINE_REQUIRED')
        engine_key=engine.strip().casefold()
        if engine_key in seen_engines:
            raise ValueError('DUPLICATE_BENCHMARK_ENGINE')
        seen_engines.add(engine_key)
        immutable(e)
        if not isinstance(e.get('case_id'),str) or not e['case_id'].strip():
            raise ValueError('CASE_ID_REQUIRED')
        if e.get('country') not in {'GB','UK','GB-NIR'} or e.get('source_kind') != 'published_real_uk':
            raise ValueError('REAL_UK_SOURCE_REQUIRED')
        for k in ('test_sha','input_sha256','case_id','mode','country'):
            if e.get(k) != anchor.get(k):
                raise ValueError('INCOMPARABLE_BENCHMARK:' + k)
        if e.get('mode') not in {'online','offline'}:
            raise ValueError('MODE_REQUIRED')
        total=e.get('total',0); correct=e.get('correct',0); unknown=e.get('unknown',0)
        skipped=e.get('skipped',0); provisional=e.get('provisional',0)
        if any(type(n) is not int or n < 0 for n in (total,correct,unknown,skipped,provisional)) or correct + unknown + skipped + provisional > total:
            raise ValueError('INVALID_BENCHMARK_COUNTS')
        if total != anchor.get('total',0):
            raise ValueError('INCOMPARABLE_BENCHMARK:eligible_denominator')
        complete=total>0 and unknown==0 and provisional==0 and skipped==0
        results.append({'engine':e.get('engine','unspecified'),'correct':correct,'total':total,
                        'unknown':unknown,'skipped':skipped,'provisional':provisional,'coverage':'complete' if complete else 'incomplete',
                        'map_sha256':e['map_sha256'],
                        'score':round(10*correct/total,2) if complete else None,'rank':None})
    ranked=sorted((r for r in results if r['coverage']=='complete'),key=lambda r:-r['score'])
    # Do not award a first-place rank without a second complete comparator.
    if len(ranked)>=2:
        for r in ranked:
            r['rank']=1+sum(other['score']>r['score'] for other in ranked)
    return results


def release_assessment(findings):
    blockers=[]; quality=[]; unknown=[]
    for f in findings:
        if f.get('status') not in STATUSES or f.get('severity') not in {'safety_critical','quality'}:
            raise ValueError('INVALID_FINDING')
        if f['status'] in {'unknown','provisional','skipped'}:
            unknown.append(f['id'])
        if f['severity']=='safety_critical' and f['status']!='passed':
            blockers.append(f['id'])
        elif f['status']=='failed':
            quality.append(f['id'])
    return {'release_allowed':not blockers,'safety_blockers':blockers,
            'quality_findings':quality,'unknowns':unknown,
            'scope':'Safety gate only; other release/acceptance gates still apply'}
