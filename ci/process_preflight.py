"""One bounded process/evidence report; no provider calls, app code publication or builds."""
import argparse
import hashlib
import json
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




def observed_emulator_network_probe(results_dir):
    """Summarize an actual runner-produced ADB transport probe, not an offline drive.

    An inconclusive device observation must not turn the normal app/unit
    checks red, but must never turn into Category 1 acceptance either.
    """
    path=Path(results_dir) / 'emulator-network-probe.json'
    if not path.exists():
        return {'status':'not_run','scope':'transport_probe_only',
                'reason_code':'NO_EMULATOR_PROBE','offline_navigation_accepted':False}
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 4096:
        raise ValueError('EMULATOR_PROBE_INVALID_FILE')
    try:
        raw=path.read_bytes()
        p=json.loads(raw)
    except (ValueError,UnicodeError,OSError) as e:
        raise ValueError('EMULATOR_PROBE_INVALID_FILE') from e
    if (not isinstance(p,dict) or p.get('schema')!=1 or
        p.get('case_id')!='emulator-network-transport-only' or
        p.get('scope')!='transport_probe_only' or
        p.get('method')!='adb_with_reachability_probe' or
        p.get('status') not in {'network_isolation_observed','inconclusive'} or
        p.get('probe_count')!=4 or
        p.get('asset_install_verified') is not False or
        p.get('navigation_offline_accepted') is not False or
        p.get('reason_code') not in {
            'CONTROL_COMMAND_FAILED','NO_ONLINE_BASELINE','PROBE_UNAVAILABLE',
            'NETWORK_REMAINED_REACHABLE','NETWORK_NOT_RESTORED',
            'NETWORK_BLOCK_OBSERVED'}):
        raise ValueError('EMULATOR_PROBE_INVALID_REPORT')
    success = (p['online_baseline_reachable'] is True and
               p.get('before',{}).get('network_reachable') is False and
               p.get('after',{}).get('network_reachable') is False and
               p['restored_reachable'] is True and
               p['reason_code']=='NETWORK_BLOCK_OBSERVED')
    if (p['status']=='network_isolation_observed') != success:
        raise ValueError('EMULATOR_PROBE_FALSE_SUCCESS')
    return {'status':p['status'],'scope':'transport_probe_only',
            'reason_code':p['reason_code'],
            'receipt_sha256':hashlib.sha256(raw).hexdigest(),
            'offline_navigation_accepted':False}

def run(app_root,summary_path):
    summary_path=Path(summary_path); summary=json.loads(summary_path.read_text())
    out=summary_path.parent
    graph=dependency_graph(app_root,ROOT)
    (out/'test-dependencies.json').write_text(json.dumps(graph,indent=1)+'\n')
    inputs={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT/'app-unit/resources').rglob('*')) if p.is_file()}
    fingerprint=hashlib.sha256(json.dumps(inputs,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    findings=json.loads((ROOT/'ci/release-findings.json').read_text())
    if findings.get('schema')!=1 or not isinstance(findings.get('findings'),list):
        raise ValueError('INVALID_SAFETY_FINDINGS')
    safety=release_assessment(findings['findings'])
    network_probe=observed_emulator_network_probe(out)
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
            'offline_acceptance':'not_established_by_receipt_validation',
            'emulator_network_probe':network_probe,
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
