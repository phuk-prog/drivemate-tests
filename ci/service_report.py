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


TRANSIENT_OUTCOMES = {'dns_failed', 'timeout', 'network_unavailable',
                      'incomplete_response', 'rate_limited', 'server_unavailable'}
KNOWN_OUTCOMES = TRANSIENT_OUTCOMES | {
    'observed_schema_only', 'authentication_required', 'forbidden_cause_unknown',
    'resource_missing_or_inaccessible', 'http_unavailable', 'redirect_blocked',
    'body_truncated', 'unsupported_encoding', 'invalid_json',
    'schema_mismatch', 'version_mismatch', 'tls_failed', 'unsafe_destination',
}


def validate_attempts(case):
    attempts = case['attempts']
    if any(not isinstance(attempt, dict) for attempt in attempts):
        raise ValueError('INVALID_SERVICE_ATTEMPT')
    for attempt in attempts:
        outcome = attempt.get('outcome')
        status = attempt.get('http_status')
        if (outcome not in KNOWN_OUTCOMES or
            (status is not None and (type(status) is not int or not 100 <= status <= 599)) or
            type(attempt.get('truncated')) is not bool or
            attempt.get('schema_status') not in ('unknown', 'mismatch', 'observed_shape_only')):
            raise ValueError('INVALID_SERVICE_ATTEMPT')
        if outcome == 'observed_schema_only':
            digest = attempt.get('body_sha256')
            if (status is None or not 200 <= status < 300 or
                attempt['truncated'] or attempt['schema_status'] != 'observed_shape_only' or
                not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest) or
                type(attempt.get('body_bytes')) is not int or attempt['body_bytes'] <= 0 or
                attempt.get('dns') != 'resolved' or attempt.get('tls') != 'verified'):
                raise ValueError('INVALID_SERVICE_SUCCESS_EVIDENCE')
    if (attempts[-1].get('outcome') != case['outcome'] or
        (len(attempts) == 2 and attempts[0].get('outcome') not in TRANSIENT_OUTCOMES) or
        case.get('recovered') is not (
            len(attempts) == 2 and case['outcome'] == 'observed_schema_only')):
        raise ValueError('CONTRADICTORY_SERVICE_ATTEMPTS')


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
        validate_attempts(case)
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
