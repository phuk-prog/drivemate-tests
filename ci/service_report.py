"""Bind bounded service observations to this CI summary without accepting product behavior."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

from service_probe import ENDPOINTS, expected_ids


def read_json(path, limit):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= limit:
        raise ValueError('INVALID_SERVICE_EVIDENCE_FILE')
    raw = path.read_bytes()
    return json.loads(raw), raw


def attach(summary_file, report_file, *, batch, run_attempt):
    summary, _ = read_json(summary_file, 4 * 1024 * 1024)
    report, raw = read_json(report_file, 1024 * 1024)
    context = report.get('context')
    if (type(report.get('schema')) is not int or report['schema'] != 1
        or report.get('scope') != 'bounded_public_service_probe' or report.get('batch') != batch
        or not isinstance(context, dict)
        or set(context) != {'app_sha','test_sha','run_id','run_attempt'}
        or any(not isinstance(value,str) for value in context.values())
        or not re.fullmatch('[0-9a-f]{40}', context['app_sha'])
        or not re.fullmatch('[0-9a-f]{40}', context['test_sha'])
        or not re.fullmatch('[0-9]+', context['run_id'])
        or not re.fullmatch('[1-9][0-9]*', context['run_attempt'])):
        raise ValueError('INVALID_SERVICE_REPORT_CONTEXT')
    if (context['app_sha'] != summary.get('app_commit')
        or context['test_sha'] != summary.get('public_tests_sha')
        or context['test_sha'] != summary.get('drivemate_tests_sha')
        or context['run_attempt'] != str(run_attempt)
        or summary.get('run_url') != 'https://github.com/phuk-prog/drivemate-tests/actions/runs/' + context['run_id']):
        raise ValueError('SERVICE_REPORT_REVISION_OR_RUN_MISMATCH')
    if any(report.get(flag) is not False for flag in ('provider_consumer_verified','navigation_accepted')):
        raise ValueError('SERVICE_REPORT_FALSE_ACCEPTANCE')
    cases = report.get('cases')
    expected = expected_ids(batch)
    if not isinstance(cases,list) or len(cases) != len(expected):
        raise ValueError('SERVICE_REPORT_COHORT_MISMATCH')
    seen, outcomes, categories = set(), Counter(), {}
    for case in cases:
        identity = case.get('id') if isinstance(case,dict) else None
        if identity not in expected or identity in seen:
            raise ValueError('SERVICE_REPORT_COHORT_MISMATCH')
        endpoint = ENDPOINTS[identity]
        if (case.get('categories') != list(endpoint.categories)
            or case.get('host') != urlsplit(endpoint.url).hostname
            or case.get('context') != context or case.get('batch') != batch
            or any(case.get(flag) is not False for flag in (
                'provider_consumer_verified','navigation_accepted','authenticated_expected_bytes_verified'))
            or not isinstance(case.get('attempts'),list) or not 1 <= len(case['attempts']) <= 2
            or not isinstance(case.get('outcome'),str)
            or not re.fullmatch('[a-z_]{1,64}',case['outcome'])):
            raise ValueError('INVALID_SERVICE_CASE')
        seen.add(identity); outcomes[case['outcome']] += 1
        for category in endpoint.categories:
            categories.setdefault(str(category),Counter())[case['outcome']] += 1
    counts = {'observations':len(cases),'outcomes':dict(outcomes)}
    if report.get('counts') != counts:
        raise ValueError('SERVICE_REPORT_COUNTER_MISMATCH')
    summary['service_observations'] = {
        'scope':report['scope'], 'context':context, 'batch':batch,
        'receipt_sha256':hashlib.sha256(raw).hexdigest(), 'counts':counts,
        'category_outcomes':{key:dict(value) for key,value in categories.items()},
        'provider_consumer_verified':False, 'navigation_accepted':False,
    }
    Path(summary_file).write_text(json.dumps(summary,indent=1)+'\n')
    return summary['service_observations']


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--summary',required=True)
    parser.add_argument('--report',required=True)
    parser.add_argument('--batch',required=True)
    parser.add_argument('--run-attempt',required=True)
    args=parser.parse_args()
    attach(args.summary,args.report,batch=args.batch,run_attempt=args.run_attempt)
