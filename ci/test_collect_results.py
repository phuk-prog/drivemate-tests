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
    def collect(self, cases, classname=CLASSNAME, source_text=None, extra_xml=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "categories/03-lane-guidance/decisions/FixtureDecisionsTest.kt"
            source.parent.mkdir(parents=True)
            source.write_text(source_text or """package com.drivemate.tests.cat03
class FixtureDecisionsTest {
    @Test fun firstBehavior() {}
    @Test fun secondBehavior() {}
}
""")
            xml = root / "junit"
            xml.mkdir()
            if cases is not None:
                body = "".join(
                    f'<testcase classname="{classname}" name="{name}">{status}</testcase>'
                    for name, status in cases
                )
                (xml / "TEST-fixture.xml").write_text(f"<testsuite>{body}</testsuite>")
            if extra_xml:
                (xml / "TEST-extra.xml").write_text(extra_xml)
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

    def all_tests(self, doc):
        return [t for g in doc["checklist"].values() for t in g["tests"]]

    def test_unknown_junit_class_is_a_failure(self):
        doc = self.collect([("firstBehavior", ""), ("secondBehavior", "")],
            extra_xml='<testsuite><testcase classname="other.UntrackedTest" name="lost"/></testsuite>')
        self.assertTrue(any(t["name"] == "lost" and t["status"] == "failed" for t in self.all_tests(doc)))

    def test_misnamed_test_source_cannot_disappear(self):
        doc = self.collect([("firstBehavior", ""), ("secondBehavior", "")], source_text='''package wrong.package
class Misnamed {
    @Test fun silentlyLost() {}
}
''')
        self.assertTrue(any(t["status"] == "failed" and "source" in t.get("failure", "").lower() for t in self.all_tests(doc)))

    def test_duplicate_junit_identity_fails(self):
        doc = self.collect([("firstBehavior", ""), ("secondBehavior", ""), ("firstBehavior", "")])
        self.assertTrue(any(t["status"] == "failed" and "duplicate" in t.get("failure", "").lower() for t in self.all_tests(doc)))

    def test_acceptance_class_uses_its_source_kind_and_cannot_disappear(self):
        doc = self.collect([("firstBehavior", "")], classname="com.drivemate.tests.cat03.FixtureAcceptanceTest",
            source_text='''package com.drivemate.tests.cat03
class FixtureAcceptanceTest {
    @Test fun firstBehavior() {}
    @Test fun secondBehavior() {}
}
''')
        tests = doc["checklist"]["3 Lane guidance: decisions"]["tests"]
        self.assertEqual({"firstBehavior": "passed", "secondBehavior": "failed"}, {t["name"]: t["status"] for t in tests})

    def test_annotated_test_method_is_still_required(self):
        doc = self.collect([("firstBehavior", ""), ("secondBehavior", "")], source_text='''package com.drivemate.tests.cat03
class FixtureDecisionsTest {
    @Test @Deprecated("test") fun annotatedBehavior() {}
}
''')
        self.assertTrue(any(t["status"] == "failed" for t in self.all_tests(doc)))


if __name__ == "__main__":
    unittest.main()
