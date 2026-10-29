"""Exercise the BASIC CLI boundary and explicit selection failure handling."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

CI = Path(__file__).resolve().parent


class BasicChecksTest(unittest.TestCase):
    def invoke(self, setup='pass'):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'basic.json'
            code = ('import sys; sys.path.insert(0, sys.argv[1]); '
                    'import run_basic_checks as basic; ' + setup +
                    '; sys.argv=["run_basic_checks.py", "--output", sys.argv[2]]; '
                    'raise SystemExit(basic.main())')
            result = subprocess.run([sys.executable, '-c', code, str(CI), str(output)],
                                    capture_output=True, text=True, timeout=20)
            return result, json.loads(output.read_text())

    def test_explicit_ten_test_smoke_and_source_checks_pass_without_release_claim(self):
        result, report = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(('BASIC', 10, 10, 10),
                         tuple(report[k] for k in ('profile', 'expected_tests', 'tests_run', 'passed')))
        self.assertEqual(10, len(set(report['selected_test_ids'])))
        self.assertTrue(all(row['status'] == 'passed' for row in report['checks']))
        self.assertIs(report['release_allowed'], False)
        self.assertEqual('not_assessed', report['category_acceptance'])

    def test_real_selected_test_failure_makes_cli_fail_and_records_failure(self):
        setup = ('import test_batch_selection; '
                 'test_batch_selection.BatchSelectionTest.test_unknown_batch_is_an_error = '
                 'lambda self: self.fail("injected runner propagation proof")')
        result, report = self.invoke(setup)
        self.assertEqual(1, result.returncode)
        self.assertEqual((9, 1, 'failed'), (report['passed'], report['failed'], report['status']))
        self.assertIn('injected runner propagation proof', report['test_problems'][0]['detail'])

    def test_missing_explicit_test_cannot_silently_reduce_suite_or_pass(self):
        result, report = self.invoke('basic.BASIC_TEST_IDS = basic.BASIC_TEST_IDS + ("missing_smoke_module.Test.test_missing",)')
        self.assertEqual(1, result.returncode)
        self.assertEqual(11, report['expected_tests'])
        self.assertGreater(report['errors'], 0)
        self.assertEqual('failed', report['status'])


if __name__ == '__main__':
    unittest.main()
