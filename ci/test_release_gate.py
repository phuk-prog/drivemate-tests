"""Public regression tests for the private app's release gate (no Android build)."""
import copy
import importlib.util
import os
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
        self.artifact = {'expired': False, 'workflow_run': {'id': 7}}
        self.run = {'id': 7, 'created_at': '2026-10-27T20:00:00Z',
                    'head_sha': self.suite, 'head_branch': 'main',
                    'path': '.github/workflows/test-app.yml',
                    'event': 'workflow_dispatch', 'status': 'completed',
                    'conclusion': 'success', 'run_attempt': 1,
                    'html_url': 'https://github.com/phuk-prog/drivemate-tests/actions/runs/7'}

    def find(self, artifacts=None, run=None):
        artifacts = [self.artifact] if artifacts is None else artifacts
        run = self.run if run is None else run
        def get(path):
            if path == 'git/ref/heads/main':
                return {'object': {'sha': self.suite}}
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
            if path.startswith('actions/artifacts?'):
                return {'artifacts': artifacts}
            return {'actions/runs/7': newer, 'actions/runs/6': older}[path]
        with patch.object(gate, 'get', side_effect=get):
            self.assertIsNone(gate.newest_run(self.app))


if __name__ == '__main__':
    unittest.main()
