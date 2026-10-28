"""Public regression tests for the private app's release gate (no Android build)."""
import copy
import base64
import importlib.util
import os
import json
from pathlib import Path
import unittest
from unittest.mock import patch

APP_ROOT = Path(os.environ.get('DRIVEMATE_APP_ROOT', 'app-src'))
SPEC = importlib.util.spec_from_file_location('release_gate', APP_ROOT / 'scripts/check_drivemate_tests.py')
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.app = 'a' * 40
        self.suite = 'b' * 40
        self.findings = []
        self.artifact = {'expired': False, 'workflow_run': {'id': 7}}
        self.run = {'id': 7, 'created_at': '2026-10-27T20:00:00Z',
                    'head_sha': self.suite, 'head_branch': 'main',
                    'path': '.github/workflows/test-app.yml',
                    'event': 'workflow_dispatch', 'status': 'completed',
                    'conclusion': 'success', 'run_attempt': 1,
                    'html_url': 'https://github.com/phuk-prog/drivemate-tests/actions/runs/7', 'public_tests_sha': self.suite}

    def find(self, artifacts=None, run=None):
        artifacts = [self.artifact] if artifacts is None else artifacts
        run = self.run if run is None else run
        def get(path):
            if path == 'git/ref/heads/main':
                return {'object': {'sha': self.suite}}
            if path == f'contents/ci/release-findings.json?ref={self.suite}':
                import json
                return {'encoding':'base64','content':base64.b64encode(json.dumps({'schema':1,'findings':self.findings}).encode()).decode()}
            if path.startswith('actions/artifacts?'):
                return {'artifacts': copy.deepcopy(artifacts), 'total_count': len(artifacts)}
            if path == 'actions/runs/7':
                return copy.deepcopy(run)
            raise AssertionError('Unexpected request: ' + path)
        with patch.object(gate, 'get', side_effect=get):
            return gate.newest_run(self.app)

    def test_current_suite_can_qualify(self):
        self.assertEqual(7, self.find()['id'])

    def test_old_suite_cannot_qualify(self):
        self.run['head_sha'] = 'c' * 40
        self.assertIsNone(self.find())

    def test_expired_artifact_cannot_qualify(self):
        self.artifact['expired'] = True
        self.assertIsNone(self.find())

    def test_other_workflow_cannot_qualify(self):
        self.run['path'] = '.github/workflows/build-signed-apk.yml'
        self.assertIsNone(self.find())

    def test_pull_request_merge_sha_cannot_qualify_for_main(self):
        self.run['event'] = 'pull_request'
        self.assertIsNone(self.find())

    def test_artifact_from_previous_attempt_cannot_qualify(self):
        self.run['run_attempt'] = 2
        self.assertIsNone(self.find())

    def test_newest_failure_must_not_fall_back_to_older_success(self):
        self.run['conclusion'] = 'failure'
        self.assertEqual('failure', self.find()['conclusion'])

    def test_no_artifact_is_no_evidence(self):
        self.assertIsNone(self.find(artifacts=[]))

    def test_refreshed_artifact_can_qualify_after_rerun(self):
        self.run['run_attempt'] = 2
        self.run['run_started_at'] = '2026-10-27T21:00:00Z'
        self.artifact['updated_at'] = '2026-10-27T21:10:00Z'
        self.assertEqual(7, self.find()['id'])

    def test_old_artifact_timestamp_cannot_qualify_after_rerun(self):
        self.run['run_attempt'] = 2
        self.run['run_started_at'] = '2026-10-27T21:00:00Z'
        self.artifact['updated_at'] = '2026-10-27T20:10:00Z'
        self.assertIsNone(self.find())

    def test_invalid_app_sha_is_rejected_before_network(self):
        with patch.object(gate, 'get') as get:
            with self.assertRaises(ValueError):
                gate.newest_run('main')
        get.assert_not_called()

    def test_newer_rerun_without_fresh_artifact_blocks_older_success(self):
        newer = dict(self.run, run_attempt=2, conclusion='failure',
                     run_started_at='2026-10-27T22:00:00Z')
        older = dict(self.run, id=6, created_at='2026-10-27T19:00:00Z')
        artifacts = [dict(self.artifact, updated_at='2026-10-27T20:30:00Z'),
                     dict(self.artifact, workflow_run={'id': 6})]
        def get(path):
            if path == 'git/ref/heads/main':
                return {'object': {'sha': self.suite}}
            if path.startswith('contents/ci/release-findings.json?ref='):
                return {'encoding':'base64','content':base64.b64encode(b'{"schema":1,"findings":[]}').decode()}
            if path.startswith('actions/artifacts?'):
                return {'artifacts': artifacts}
            return {'actions/runs/7': newer, 'actions/runs/6': older}[path]
        with patch.object(gate, 'get', side_effect=get):
            self.assertIsNone(gate.newest_run(self.app))

    def test_open_safety_critical_issue_blocks_release_even_when_components_pass(self):
        self.findings=[{'id':'J27-03','severity':'safety_critical','status':'unknown'}]
        with self.assertRaisesRegex(ValueError,'SAFETY_RELEASE_BLOCKER'):
            self.find()

    def test_quality_findings_do_not_masquerade_as_safety_failures(self):
        self.findings=[{'id':'camera','severity':'quality','status':'failed'}]
        self.assertEqual(7,self.find()['id'])

    def test_successful_run_requires_valid_downloaded_summary(self):
        summary = {'app_commit':self.app, 'public_tests_sha':self.suite,
            'drivemate_tests_sha':self.suite, 'total':1, 'passed':1,
            'tests':[{'class':'FixtureTest','name':'case','status':'passed'}], 'diagnostics':[],
            'run_url':self.run['html_url'], 'run_attempt':1,
            'crashed':False, 'not_responding':False}
        def download(args, check):
            Path(args[-1], 'summary.json').write_text(json.dumps(summary))
        for changes in ({}, {'public_tests_sha':'c'*40}, {'total':2},
                        {'diagnostics':['failure']}, {'crashed':True},
                        {'tests':[{'status':'unknown'}]}, {'tests':[{'status':'passed'}]},
                        {'tests':[{'class':'FixtureTest','name':'','status':'passed'}]}, {'run_url':'https://github.com/other/repo/actions/runs/7'},
                        {'run_attempt':2}, {'total':2, 'passed':2, 'tests':[{'class':'FixtureTest','name':'case','status':'passed'}]*2}):
            with self.subTest(changes=changes):
                candidate = dict(summary, **changes)
                def download(args, check):
                    Path(args[-1], 'summary.json').write_text(json.dumps(candidate))
                with patch.object(gate.subprocess, 'run', side_effect=download):
                    if changes:
                        with self.assertRaises(ValueError):
                            gate.check_summary(self.run, self.app, self.suite)
                    else:
                        gate.check_summary(self.run, self.app, self.suite)

    def test_gate_checks_summary_before_returning_success(self):
        with patch.object(gate, 'newest_run', return_value=self.run), \
             patch.object(gate, 'check_summary', side_effect=ValueError('BAD_EVIDENCE')) as check:
            with self.assertRaisesRegex(ValueError, 'BAD_EVIDENCE'):
                gate.main(self.app)
            check.assert_called_once_with(self.run, self.app, self.suite)


if __name__ == '__main__':
    unittest.main()
