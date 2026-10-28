"""Frozen TF-05 regressions: real suite identity guard must fail closed."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("suite_inventory", Path(__file__).with_name("suite_inventory.py"))
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)

REL = "categories/03-lane-guidance/decisions/SampleTest.kt"
SOURCE = "package com.drivemate.tests.cat03\nclass SampleTest {\n @Test fun first() {}\n @Test fun second() {}\n}\n"


def blob(content):
    data = content.encode()
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


class ApprovedSuiteInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / REL
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(SOURCE)
        self.inventory = self.root / "ci/approved_suite_inventory.json"
        self.inventory.parent.mkdir(parents=True)
        self.data = {"schema": 1, "baseline_commit": "a" * 40,
                     "files": {REL: blob(SOURCE)}}
        self.save()

    def save(self):
        self.inventory.write_text(json.dumps(self.data))

    def check(self):
        return audit.inspect(self.root, self.inventory)

    def test_baseline_passes_as_source_only_not_release_acceptance(self):
        report = self.check()
        self.assertEqual(1, report["source_files"])
        self.assertEqual(2, report["discoverable_test_methods"])
        self.assertEqual("verified_source_identity", report["status"])
        self.assertIs(report["android_execution_accepted"], False)
        self.assertIs(report["release_allowed"], False)

    def test_deleted_file_does_not_silently_reduce_denominator(self):
        self.path.unlink()
        with self.assertRaisesRegex(ValueError, "MISSING_APPROVED_TEST_SOURCE"):
            self.check()

    def test_renamed_source_does_not_silently_reduce_denominator(self):
        self.path.rename(self.path.with_name("RenamedTest.kt"))
        with self.assertRaisesRegex(ValueError, "MISSING_APPROVED_TEST_SOURCE"):
            self.check()

    def test_new_test_file_requires_approved_inventory_revision(self):
        other = self.path.with_name("ExtraTest.kt")
        other.write_text(SOURCE.replace("SampleTest", "ExtraTest"))
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_TEST_SOURCE"):
            self.check()

    def test_removed_method_fails_even_if_remaining_test_is_green(self):
        self.path.write_text(SOURCE.replace(" @Test fun second() {}\n", ""))
        with self.assertRaisesRegex(ValueError, "TEST_SOURCE_BLOB_CHANGED"):
            self.check()

    def test_renamed_method_requires_explicit_inventory_update(self):
        self.path.write_text(SOURCE.replace("second()", "different()"))
        with self.assertRaisesRegex(ValueError, "TEST_SOURCE_BLOB_CHANGED"):
            self.check()

    def test_duplicate_annotated_method_is_invalid_even_if_blob_approved(self):
        changed = SOURCE.replace("second()", "first()")
        self.path.write_text(changed)
        self.data["files"][REL] = blob(changed)
        self.save()
        with self.assertRaisesRegex(ValueError, "DUPLICATE_TEST_METHOD"):
            self.check()

    def test_frozen_file_without_tests_is_rejected(self):
        changed = SOURCE.replace("@Test", "@Disabled")
        self.path.write_text(changed)
        self.data["files"][REL] = blob(changed)
        self.save()
        with self.assertRaisesRegex(ValueError, "NO_DISCOVERABLE_TEST_METHODS"):
            self.check()

    def test_wrong_class_identity_rejected_despite_approved_blob(self):
        changed = SOURCE.replace("class SampleTest", "class WrongTest")
        self.path.write_text(changed)
        self.data["files"][REL] = blob(changed)
        self.save()
        with self.assertRaisesRegex(ValueError, "TEST_CLASS_IDENTITY_INVALID"):
            self.check()

    def test_corrupt_inventory_fails_closed(self):
        self.inventory.write_text("not json")
        with self.assertRaisesRegex(ValueError, "SUITE_INVENTORY_INVALID"):
            self.check()

    def test_manifest_cannot_be_replaced_by_empty_allowlist(self):
        self.data["files"] = {}
        self.save()
        with self.assertRaisesRegex(ValueError, "SUITE_INVENTORY_INVALID"):
            self.check()

    def test_no_symlink_source_escape(self):
        target = self.root / "external.kt"
        target.write_text(SOURCE)
        self.path.unlink()
        self.path.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "INVALID_TEST_SOURCE_FILE"):
            self.check()


if __name__ == "__main__":
    unittest.main()
