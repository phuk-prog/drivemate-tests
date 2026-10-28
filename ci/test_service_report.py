"""Execute the real service-summary writer; product observations cannot alter test totals."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from urllib.parse import urlsplit

from service_probe import ENDPOINTS, expected_ids
from service_report import attach


class ServiceReportContracts(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.summary = self.root / 'summary.json'
        self.report = self.root / 'service-probe.json'
        self.context = {'app_sha': 'a'*40, 'test_sha': 'b'*40, 'run_id': '123', 'run_attempt': '2'}
        self.original = {'app_commit': 'a'*40, 'public_tests_sha': 'b'*40,
                         'drivemate_tests_sha': 'b'*40,
                         'run_url': 'https://github.com/phuk-prog/drivemate-tests/actions/runs/123',
                         'passed': 292, 'total': 292, 'diagnostics': []}
        ids = sorted(expected_ids('A'))
        self.receipt = {
            'schema': 1, 'scope': 'bounded_public_service_probe', 'context': self.context,
            'batch': 'A', 'provider_consumer_verified': False, 'navigation_accepted': False,
            'cases': [{'id': identity, 'categories': list(ENDPOINTS[identity].categories),
                       'host': urlsplit(ENDPOINTS[identity].url).hostname,
                       'context': self.context, 'batch': 'A', 'outcome': 'forbidden_cause_unknown',
                       'attempts': [{'outcome': 'forbidden_cause_unknown'}],
                       'provider_consumer_verified': False, 'navigation_accepted': False,
                       'authenticated_expected_bytes_verified': False} for identity in ids],
            'counts': {'observations': len(ids), 'outcomes': {'forbidden_cause_unknown': len(ids)}}}

    def write(self):
        self.summary.write_text(json.dumps(self.original))
        self.report.write_text(json.dumps(self.receipt))

    def execute(self):
        self.write()
        attach(self.summary, self.report, batch='A', run_attempt='2')
        return json.loads(self.summary.read_text())

    def test_actual_receipt_hash_and_unknown_outcomes_preserve_passing_test_results(self):
        result = self.execute()
        for key, value in self.original.items():
            self.assertEqual(value, result[key])
        observed = result['service_observations']
        self.assertEqual(hashlib.sha256(self.report.read_bytes()).hexdigest(), observed['receipt_sha256'])
        self.assertEqual(self.receipt['counts'], observed['counts'])
        self.assertFalse(observed['provider_consumer_verified'])
        self.assertFalse(observed['navigation_accepted'])
        self.assertTrue(self.summary.read_text().endswith('\n'))

    def test_wrong_app_suite_run_or_attempt_receipt_is_rejected(self):
        for key, value in [('app_sha','c'*40),('test_sha','c'*40),('run_id','456'),('run_attempt','1')]:
            with self.subTest(key=key):
                previous = self.receipt['context'][key]
                self.receipt['context'][key] = value
                with self.assertRaises(ValueError): self.execute()
                self.receipt['context'][key] = previous

    def test_missing_duplicate_or_wrong_category_cases_are_rejected(self):
        valid = copy.deepcopy(self.receipt)
        mutations = [lambda r:r['cases'].pop(), lambda r:r['cases'].append(copy.deepcopy(r['cases'][0])),
                     lambda r:r['cases'][0].update(categories=[23]),
                     lambda r:r['cases'][0].update(host='unreviewed.invalid')]
        for mutate in mutations:
            self.receipt = copy.deepcopy(valid); mutate(self.receipt)
            with self.assertRaises(ValueError): self.execute()

    def test_counter_tampering_and_unselected_batch_are_rejected(self):
        self.receipt['counts']['observations'] += 1
        with self.assertRaises(ValueError): self.execute()
        self.receipt['counts']['observations'] -= 1
        self.receipt['batch'] = 'B'
        with self.assertRaises(ValueError): self.execute()

    def test_declared_product_acceptance_or_unsanitized_outcome_is_rejected(self):
        original = copy.deepcopy(self.receipt)
        mutations = [lambda r:r.update(navigation_accepted=True),
                     lambda r:r['cases'][0].update(provider_consumer_verified=True),
                     lambda r:r['cases'][0].update(outcome='https://secret.invalid/?token=private')]
        for mutate in mutations:
            self.receipt=copy.deepcopy(original); mutate(self.receipt)
            with self.assertRaises(ValueError): self.execute()

    def test_missing_or_symlink_receipt_fails_closed(self):
        self.write(); self.report.unlink()
        with self.assertRaises(ValueError): attach(self.summary,self.report,batch='A',run_attempt='2')
        target=self.root/'other.json'; target.write_text(json.dumps(self.receipt)); self.report.symlink_to(target)
        with self.assertRaises(ValueError): attach(self.summary,self.report,batch='A',run_attempt='2')
