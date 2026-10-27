"""Regression checks for the gate that must account for every expected Kotlin test."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


COLLECTOR = Path(__file__).with_name("collect_results.py").resolve()
CLASSNAME = "com.drivemate.tests.cat03.FixtureDecisionsTest"


class CollectResultsTest(unittest.TestCase):
    def collect(self, cases, classname=CLASSNAME):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "categories/03-lane-guidance/decisions/FixtureDecisionsTest.kt"
            source.parent.mkdir(parents=True)
            source.write_text("""package com.drivemate.tests.cat03
class FixtureDecisionsTest {
    @Test fun firstBehavior() {}
    @Test fun secondBehavior() {}
}
""".replace('FixtureDecisionsTest', classname.rsplit('.', 1)[-1]))
            xml = root / "junit"
            xml.mkdir()
            if cases is not None:
                body = "".join(
                    f'<testcase classname="{classname}" name="{name}">{status}</testcase>'
                    for name, status in cases
                )
                (xml / "TEST-fixture.xml").write_text(f"<testsuite>{body}</testsuite>")
            output = root / "results/full-app.json"
            subprocess.run(
                [sys.executable, str(COLLECTOR), str(output), "https://example.test/run", "app-commit", "tests-commit", "media", str(xml)],
                cwd=root, check=True, capture_output=True, text=True,
            )
            return json.loads(output.read_text())

    def test_missing_test_fails_even_when_another_test_passes(self):
        document = self.collect([("firstBehavior", "")])
        tests = document["checklist"]["3 Lane guidance: decisions"]["tests"]
        self.assertEqual({"firstBehavior": "passed", "secondBehavior": "failed"}, {test["name"]: test["status"] for test in tests})
        missing = next(test for test in tests if test["name"] == "secondBehavior")
        self.assertIn("Expected test did not run", missing["failure"])

    def test_entire_missing_suite_fails_every_expected_test(self):
        document = self.collect(None)
        tests = document["checklist"]["3 Lane guidance: decisions"]["tests"]
        self.assertEqual(2, len(tests))
        self.assertTrue(all(test["status"] == "failed" for test in tests))

    def test_complete_suite_preserves_metadata_and_passes(self):
        document = self.collect([("firstBehavior", ""), ("secondBehavior", "")])
        self.assertEqual("app-commit", document["app_commit"])
        self.assertEqual("tests-commit", document["drivemate_tests_sha"])
        tests = document["checklist"]["3 Lane guidance: decisions"]["tests"]
        self.assertEqual(2, len(tests))
        self.assertTrue(all(test["status"] == "passed" for test in tests))

    def test_skipped_test_does_not_count_as_passed_or_disappear(self):
        document = self.collect([("firstBehavior", ""), ("secondBehavior", "<skipped/>")])
        tests = document["checklist"]["3 Lane guidance: decisions"]["tests"]
        self.assertEqual({"firstBehavior": "passed", "secondBehavior": "skipped"}, {test["name"]: test["status"] for test in tests})

    def test_acceptance_named_tests_are_collected_by_source_directory(self):
        document = self.collect([('firstBehavior', ''), ('secondBehavior', '')],
                                'com.drivemate.tests.cat03.LaneContinuityAcceptanceTest')
        self.assertIn('3 Lane guidance: decisions', document['checklist'])
        tests = document['checklist']['3 Lane guidance: decisions']['tests']
        self.assertEqual(2, len(tests))
        self.assertTrue(all(test['status'] == 'passed' for test in tests))

    def test_missing_acceptance_named_tests_fail_individually(self):
        document = self.collect(None, 'com.drivemate.tests.cat03.LaneContinuityAcceptanceTest')
        self.assertIn('3 Lane guidance: decisions', document['checklist'])
        tests = document['checklist']['3 Lane guidance: decisions']['tests']
        self.assertEqual({'firstBehavior', 'secondBehavior'}, {test['name'] for test in tests})
        self.assertTrue(all(test['status'] == 'failed' for test in tests))


if __name__ == "__main__":
    unittest.main()

