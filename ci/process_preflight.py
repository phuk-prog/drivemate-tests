"""One bounded process/evidence report; no provider calls, app code publication or builds."""
import argparse
import hashlib
import json
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
    acceptance=[validate_acceptance(e) for e in summary.get('acceptance_evidence',[])]
    truths=list((ROOT/'evidence/legal-truth').glob('*.json'))
    for p in truths: independent_truth(json.loads(p.read_text()))
    benchmarks=[]
    for p in sorted((ROOT/'evidence/benchmarks').glob('*.json')):
        benchmarks.append({'evidence_file':str(p.relative_to(ROOT)),
                           'results':validate_benchmark(json.loads(p.read_text()))})
    retests=retest_records(ROOT/'evidence/retests')
    report={'schema':1,'app_sha':summary['app_commit'],'test_sha':summary['public_tests_sha'],
            'input_sha256':fingerprint,'source_inputs':inputs,'safety_release_gate':safety,
            'source_to_screen_records':len(acceptance),'independent_truth_records':len(truths),
            'benchmarks':benchmarks,'benchmark_status':'validated_records' if benchmarks else 'not_provided',
            'frozen_retests':retests,
            'limitations':['Framework validation is not category or real-drive acceptance',
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
