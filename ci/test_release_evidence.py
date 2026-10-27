import copy
import unittest
from pathlib import Path

from release_evidence import validate

APP = "a" * 40
SUITE = "b" * 40


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.summary = {"app_commit": APP, "public_tests_sha": SUITE, "drivemate_tests_sha": SUITE,
            "passed": 1, "total": 1, "tests": [{"status": "passed"}], "diagnostics": [],
            "crashed": False, "not_responding": False}

    def test_matching_complete_evidence_is_accepted(self):
        validate(self.summary, APP, SUITE)

    def test_stale_suite_and_wrong_app_are_rejected(self):
        for key in ("app_commit", "public_tests_sha", "drivemate_tests_sha"):
            with self.subTest(key=key):
                summary = copy.deepcopy(self.summary)
                summary[key] = "c" * 40
                with self.assertRaises(ValueError):
                    validate(summary, APP, SUITE)

    def test_empty_incomplete_and_miscounted_evidence_are_rejected(self):
        for changes in ({"passed": 0}, {"total": 0, "passed": 0}, {"total": True},
                        {"tests": []}, {"diagnostics": [{"message": "compile failed"}]},
                        {"crashed": True}, {"not_responding": True}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate({**self.summary, **changes}, APP, SUITE)

    def test_nonpass_acceptance_states_are_rejected(self):
        for status in ("failed", "skipped", "unknown", "provisional"):
            with self.subTest(status=status), self.assertRaises(ValueError):
                validate({**self.summary, "tests": [{"status": status}]}, APP, SUITE)

    def test_missing_health_fields_are_rejected(self):
        for key in ("diagnostics", "crashed", "not_responding"):
            summary = dict(self.summary)
            del summary[key]
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(summary, APP, SUITE)

    def test_moving_revision_names_are_rejected(self):
        with self.assertRaises(ValueError):
            validate(self.summary, APP, "main")

    def test_signing_workflow_pins_and_validates_the_public_suite(self):
        workflow = (Path(__file__).resolve().parents[1] /
            ".github/workflows/build-signed-apk.yml").read_text()
        self.assertIn('python3 ci/release_evidence.py "$evidence/summary.json" "$sha" "$GITHUB_SHA"', workflow)
        self.assertIn('--arg suite "$GITHUB_SHA"', workflow)
        self.assertIn('.head_sha == $suite', workflow)
