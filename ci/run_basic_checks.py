#!/usr/bin/env python3
"""Small, explicit, offline smoke profile; never release or category evidence."""
import argparse
import ast
import io
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
# Individual IDs keep additions to the full suite from silently expanding BASIC.
BASIC_TEST_IDS = (
    'test_batch_selection.BatchSelectionTest.test_lane_batch_keeps_lane_checks_and_excludes_unrelated_voice',
    'test_batch_selection.BatchSelectionTest.test_unit_checks_remain_in_every_batch',
    'test_batch_selection.BatchSelectionTest.test_all_selects_every_historical_category',
    'test_batch_selection.BatchSelectionTest.test_unknown_batch_is_an_error',
    'test_batch_selection.BatchSelectionTest.test_noncategory_kotlin_source_cannot_silently_disappear',
    'test_suite_inventory.ApprovedSuiteInventoryTest.test_baseline_passes_as_source_only_not_release_acceptance',
    'test_suite_inventory.ApprovedSuiteInventoryTest.test_deleted_file_does_not_silently_reduce_denominator',
    'test_suite_inventory.ApprovedSuiteInventoryTest.test_manifest_cannot_be_replaced_by_empty_allowlist',
    'test_process_contract.ProcessContractTest.test_unassigned_component_results_do_not_become_category_acceptance',
    'test_process_contract.ProcessContractTest.test_safety_failures_block_release_separately_from_quality',
)


def run(root=ROOT):
    root = Path(root)
    sys.path.insert(0, str(root / 'ci'))
    checks = []
    for name, operation in (
        ('python_syntax_ci_tools', lambda: syntax_check(root)),
        ('approved_kotlin_source_inventory', lambda: inventory_check(root)),
    ):
        try:
            details = operation()
            checks.append({'name': name, 'status': 'passed', **details})
        except Exception as exc:
            checks.append({'name': name, 'status': 'failed', 'error': str(exc)[:1000]})
    suite = unittest.TestSuite()
    loader = unittest.TestLoader()
    for test_id in BASIC_TEST_IDS:
        suite.addTests(loader.loadTestsFromName(test_id))
    log = io.StringIO()
    result = unittest.TextTestRunner(stream=log, verbosity=1).run(suite)
    print(log.getvalue(), file=sys.stderr, end='')
    failed = len(result.failures)
    errors = len(result.errors)
    skipped = len(result.skipped)
    passed = result.testsRun - failed - errors - skipped
    complete = (result.testsRun == len(BASIC_TEST_IDS) and not loader.errors
                and not failed and not errors and not skipped)
    ok = complete and all(check['status'] == 'passed' for check in checks)
    return {
        'schema': 1, 'profile': 'BASIC', 'scope': 'offline_python_smoke_and_source_only',
        'release_allowed': False, 'android_execution_accepted': False,
        'category_acceptance': 'not_assessed', 'legal_acceptance': 'not_assessed',
        'status': 'passed' if ok else 'failed',
        'selected_test_ids': list(BASIC_TEST_IDS),
        'expected_tests': len(BASIC_TEST_IDS), 'tests_run': result.testsRun,
        'passed': passed, 'failed': failed, 'errors': errors, 'skipped': skipped,
        'checks': checks,
        'test_problems': [{'test': test.id(), 'detail': detail[-2000:]}
                          for test, detail in result.failures + result.errors],
    }


def syntax_check(root):
    paths = sorted(p for folder in ('ci', 'tools') for p in (root / folder).rglob('*.py'))
    if not paths:
        raise ValueError('NO_PYTHON_SOURCE')
    for path in paths:
        ast.parse(path.read_bytes(), filename=str(path.relative_to(root)))
    return {'files_checked': len(paths)}


def inventory_check(root):
    from suite_inventory import inspect
    report = inspect(root, root / 'ci/approved_suite_inventory.json')
    return {'source_files': report['source_files'],
            'discoverable_test_methods': report['discoverable_test_methods']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'results/basic.json')
    args = parser.parse_args()
    report = run()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f"BASIC {report['passed']}/{report['expected_tests']} Python tests; "
          f"{sum(c['status'] == 'passed' for c in report['checks'])}/{len(report['checks'])} source checks; "
          'release_allowed=false')
    return 0 if report['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
