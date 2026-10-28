"""One bounded process/evidence report; no provider calls, app code publication or builds."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from dependency_graph import build as dependency_graph
from process_contract import release_assessment, validate_acceptance, validate_benchmark, independent_truth, validate_retest

ROOT=Path(__file__).resolve().parents[1]


def retest_records(directory):
    records=[]
    for path in sorted(Path(directory).glob('*.json')):
        r=json.loads(path.read_text())
        if r.get('schema')!=1: raise ValueError('INVALID_RETEST_RECORD')
        validate_retest(r['before'],r['after'])
        if r['before'].get('outcome')!='failed' or r['after'].get('outcome')!='passed':
            raise ValueError('RED_GREEN_PROOF_REQUIRED')
        for phase in ('before','after'):
            p=r[phase]
            log=path.parent / p['log_file']
            if log.parent.resolve()!=path.parent.resolve() or hashlib.sha256(log.read_bytes()).hexdigest()!=p['log_sha256']:
                raise ValueError('RETEST_LOG_INTEGRITY')
        records.append({'case_id':r['after']['case_id'],'test_sha':r['after']['test_sha'],
                        'before_app_sha':r['before']['app_sha'],'after_app_sha':r['after']['app_sha'],
                        'status':'identical_frozen_retest_validated'})
    return records



def verify_offline_artifacts(e, results_dir):
    """Cross-check offline proof against local runner files, never self-declared digests.

    Only files observed during this exact CI run may qualify. This checks
    integrity and trace consistency, NOT whether a runtime actually produced
    a truthful network probe; real offline acceptance needs a trusted runner.
    """
    proof=e['offline_proof']
    files=proof.get('artifacts')
    if not isinstance(files, dict):
        raise ValueError('OFFLINE_ARTIFACT_MISSING:artifacts')
    root=Path(results_dir) / 'offline-evidence'
    if root.is_symlink():
        raise ValueError('OFFLINE_ARTIFACT_PATH_INVALID:directory')
    expected={
        'network_probe':proof['network']['probe_sha256'],
        'catalog':proof['delivery']['catalog_sha256'],
        'manifest':proof['delivery']['manifest_sha256'],
        'installed':proof['delivery']['installed_sha256'],
        'readback':proof['delivery']['read_sha256'],
    }
    paths={}
    for kind, sha in expected.items():
        name=files.get(kind)
        # Only single-component filenames inside the dedicated evidence dir.
        # No traversal, arbitrary private-file reads or symlink escapes.
        if (not isinstance(name,str) or len(name)>80
            or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*',name)
            or '..' in name):
            raise ValueError('OFFLINE_ARTIFACT_PATH_INVALID:'+kind)
        path=root/name
        if path.is_symlink() or (path.exists() and not path.is_file()):
            raise ValueError('OFFLINE_ARTIFACT_PATH_INVALID:'+kind)
        if not path.is_file():
            raise ValueError('OFFLINE_ARTIFACT_MISSING:'+kind)
        if kind in ('network_probe','catalog','manifest') and path.stat().st_size>10*1024*1024:
            raise ValueError('OFFLINE_ARTIFACT_TOO_LARGE:'+kind)
        h=hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda:stream.read(1024*1024),b''):
                h.update(block)
        if h.hexdigest()!=sha:
            raise ValueError('OFFLINE_ARTIFACT_DIGEST_MISMATCH:'+kind)
        paths[kind]=path
    try:
        trace=json.loads(paths['network_probe'].read_text())
        if (not isinstance(trace,dict) or trace.get('schema')!=1
            or trace.get('case_id')!=e['case_id']
            or trace.get('method')!=proof['network']['method']
            or not isinstance(trace.get('before'),dict)
            or not isinstance(trace.get('after'),dict)
            or trace['before'].get('network_reachable') is not False
            or trace['after'].get('network_reachable') is not False):
            raise ValueError('OFFLINE_NETWORK_TRACE_INVALID')
    except (UnicodeError,json.JSONDecodeError):
        raise ValueError('OFFLINE_NETWORK_TRACE_INVALID') from None
    return {'status':'verified','artifact_count':len(expected),'case_id':e['case_id']}



def emulator_smoke_report(path, source_sha):
    """Bounded, fail-closed source for environment status, never route acceptance."""
    path=Path(path)
    if not path.exists():
        return {'status':'not_run','reason_code':'EMULATOR_NOT_PROBED',
                'scope':'emulator_environment_smoke_only','offline_acceptance':False}
    if path.is_symlink() or path.stat().st_size>4096:
        raise ValueError('EMULATOR_SMOKE_INVALID_FILE')
    raw=json.loads(path.read_text())
    if (raw.get('schema')!=1 or raw.get('scope')!='emulator_environment_smoke_only'
        or raw.get('source_kind')!='archived_real_uk_fixture'
        or raw.get('source_sha256')!=source_sha
        or raw.get('published_release_downloaded') is not False
        or raw.get('navigation_tested') is not False
        or raw.get('offline_acceptance') is not False
        or raw.get('status') not in {'observed','inconclusive'}
        or not isinstance(raw.get('network'),dict)):
        raise ValueError('EMULATOR_SMOKE_INVALID_SCOPE')
    fields=('online_before','reachable_isolated_before_readback',
            'reachable_isolated_after_readback')
    network=raw['network']
    if network.get('method')!='adb_direct_reachability' or any(
            network.get(k) is not None and type(network[k]) is not bool
            for k in fields):
        raise ValueError('EMULATOR_SMOKE_INVALID_NETWORK')
    if raw['status']=='observed':
        if (raw.get('reason_code') is not None or network.get('online_before') is not True
            or network.get('reachable_isolated_before_readback') is not False
            or network.get('reachable_isolated_after_readback') is not False
            or raw.get('installed_sha256')!=source_sha
            or raw.get('readback_sha256')!=source_sha):
            raise ValueError('EMULATOR_SMOKE_FALSE_OBSERVATION')
    else:
        if raw.get('reason_code') not in {
            'BASELINE_UNVERIFIED','NETWORK_REACHABLE_WHILE_ISOLATED',
            'READBACK_DIGEST_MISMATCH','EMULATOR_EVIDENCE_COMMAND_FAILED','RESTORE_FAILED'
        }:
            raise ValueError('EMULATOR_SMOKE_INCOMPLETE_REASON')
    return {'status':raw['status'],'reason_code':raw.get('reason_code'),
            'scope':'emulator_environment_smoke_only',
            'source_sha256':source_sha,'offline_acceptance':False,
            'navigation_tested':False,'published_release_downloaded':False}



def emulator_smoke_record(out, required=False):
    """Verify bounded reports from the device smoke, *never* offline acceptance."""
    path=Path(out)/'emulator-evidence.json'
    if not path.exists():
        if required:
            raise ValueError('EMULATOR_SMOKE_MISSING')
        return {'status':'not_collected','scope':'emulator_smoke_only'}
    if path.is_symlink() or not path.is_file() or path.stat().st_size>8192:
        raise ValueError('EMULATOR_SMOKE_INVALID')
    try:
        record=json.loads(path.read_text())
    except (OSError,UnicodeError,json.JSONDecodeError):
        raise ValueError('EMULATOR_SMOKE_INVALID') from None
    fixture=ROOT/'app-unit/resources/cat3-published-manchester-lanes-3.json'
    expected=hashlib.sha256(fixture.read_bytes()).hexdigest()
    if (record.get('schema')!=1 or record.get('scope')!='emulator_smoke_only'
        or record.get('source')!='frozen_public_test_fixture_not_live_release'
        or record.get('probe_method')!='adb_ping_icmp_smoke_not_app_connectivity'
        or record.get('fixture_sha256')!=expected
        or record.get('device_readback_sha256')!=expected
        or record.get('file_integrity_verified') is not True
        or record.get('published_release_download_verified') is not False
        or record.get('offline_navigation_accepted') is not False
        or type(record.get('network_isolation_observed')) is not bool):
        raise ValueError('EMULATOR_SMOKE_INVALID')
    if record['network_isolation_observed'] and (
            record.get('baseline_reachable') is not True
            or record.get('isolation_probe_reachable') is not False
            or record.get('recovery_reachable') is not True
            or record.get('network_reason')!='probe_blocked'):
        raise ValueError('EMULATOR_SMOKE_FALSE_ISOLATION')
    # Report only a compact set of observed metrics, never raw device data.
    return {'status':'device_readback_verified',
            'scope':'emulator_smoke_only',
            'network_isolation_observed':record['network_isolation_observed'],
            'network_reason':record.get('network_reason','unknown'),
            'published_release_download_verified':False,
            'offline_navigation_accepted':False}

def run(app_root,summary_path):
    summary_path=Path(summary_path); summary=json.loads(summary_path.read_text())
    out=summary_path.parent
    graph=dependency_graph(app_root,ROOT)
    (out/'test-dependencies.json').write_text(json.dumps(graph,indent=1)+'\n')
    inputs={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT/'app-unit/resources').rglob('*')) if p.is_file()}
    fingerprint=hashlib.sha256(json.dumps(inputs,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    fixture_sha=inputs.get('app-unit/resources/cat3-published-manchester-lanes-3.json')
    if not fixture_sha: raise ValueError('EMULATOR_SMOKE_SOURCE_MISSING')
    smoke=emulator_smoke_report(out/'emulator-evidence-smoke.json',fixture_sha)
    findings=json.loads((ROOT/'ci/release-findings.json').read_text())
    if findings.get('schema')!=1 or not isinstance(findings.get('findings'),list):
        raise ValueError('INVALID_SAFETY_FINDINGS')
    safety=release_assessment(findings['findings'])
    smoke=emulator_smoke_record(out,required=os.getenv('EMULATOR_SMOKE_REQUIRED')=='true')
    acceptance_records=summary.get('acceptance_evidence',[])
    acceptance=[validate_acceptance(e) for e in acceptance_records]
    offline_checks=[verify_offline_artifacts(e,out) for e in acceptance_records
                    if e.get('mode')=='offline']
    truths=list((ROOT/'evidence/legal-truth').glob('*.json'))
    for p in truths: independent_truth(json.loads(p.read_text()))
    benchmarks=[]
    for p in sorted((ROOT/'evidence/benchmarks').glob('*.json')):
        benchmarks.append({'evidence_file':str(p.relative_to(ROOT)),
                           'results':validate_benchmark(json.loads(p.read_text()))})
    retests=retest_records(ROOT/'evidence/retests')
    report={'schema':1,'app_sha':summary['app_commit'],'test_sha':summary['public_tests_sha'],
            'input_sha256':fingerprint,'source_inputs':inputs,'safety_release_gate':safety,
            'source_to_screen_records':len(acceptance),'offline_receipts_verified':len(offline_checks),
            'emulator_smoke':smoke,
            'offline_acceptance':'not_established_by_receipt_validation',
            'emulator_environment_smoke':smoke,
            'independent_truth_records':len(truths),
            'benchmarks':benchmarks,'benchmark_status':'validated_records' if benchmarks else 'not_provided',
            'frozen_retests':retests,
            'limitations':['Framework validation is not category or real-drive acceptance',
                           'Offline proof files are checked for hash and trace consistency, not runtime authenticity',
                           'Zero truth/benchmark records is missing evidence, not a passing comparison',
                           'Dependency edges describe observed test imports, not an exhaustive runtime graph']}
    summary['process']=report
    summary_path.write_text(json.dumps(summary,indent=1)+'\n')
    (out/'process-summary.json').write_text(json.dumps(report,indent=1)+'\n')
    print(json.dumps({'source_to_screen_records':len(acceptance),'truth_records':len(truths),
                      'benchmark_status':report['benchmark_status'],'dependency_edges':len(graph['edges']),
                      'release_allowed':safety['release_allowed']},sort_keys=True))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--app-root',default='app-src'); p.add_argument('--summary',required=True)
    a=p.parse_args()
    try: run(a.app_root,a.summary)
    except (ValueError,KeyError,TypeError,OSError) as e:
        target=Path(a.summary)
        if target.is_file():
            d=json.loads(target.read_text())
            d.setdefault('diagnostics',[]).append({'stage':'process','message':'PROCESS_GATE_FAILED:'+str(e)[:120]})
            target.write_text(json.dumps(d,indent=1)+'\n')
        raise
