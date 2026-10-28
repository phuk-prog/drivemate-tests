"""Emulator fixture file transport must be *observed*, not described as an offline drive.

This suite uses synthetic device responses to test the tool, never synthetic
UK roads or an invented published-release download.
"""
import hashlib
import importlib
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


class Device:
    def __init__(self, *, wrong_install=False, corrupted_read=False, fail_read=False):
        self.wrong_install=wrong_install
        self.corrupted_read=corrupted_read
        self.fail_read=fail_read
        self.removed=False
        self.stored=b""
        self.calls=[]

    def install(self,data):
        self.calls.append("install")
        self.stored=data
        return sha(data+b"bad") if self.wrong_install else sha(data)

    def readback(self):
        self.calls.append("readback")
        if self.fail_read: raise OSError("Device cannot read file")
        return self.stored+b"x" if self.corrupted_read else self.stored

    def remove(self):
        self.calls.append("remove")
        self.removed=True


class EmulatorFixtureReadbackTest(unittest.TestCase):
    def setUp(self):
        path=ROOT/"ci/emulator_fixture_readback.py"
        self.assertTrue(path.is_file(),"DEVICE_FIXTURE_READBACK_GENERATOR_MISSING")
        self.module=importlib.import_module("emulator_fixture_readback")
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source=Path(self.tmp.name)/"archived_uk.json"
        self.source.write_bytes(b'{"UK_source":"archived-real-osm-derived-fixture"}')
        self.output=Path(self.tmp.name)/"emulator-fixture-readback.json"

    def capture(self,d):
        row=self.module.capture(self.source,self.output,d)
        self.assertEqual(row,json.loads(self.output.read_text()))
        self.assertTrue(d.removed,"Never leave a test fixture on device")
        self.assertFalse(row["offline_acceptance"])
        self.assertFalse(row["published_release_downloaded"])
        self.assertFalse(row["production_map_cache_loaded"])
        return row

    def test_real_device_install_and_readback_must_match_frozen_bytes(self):
        d=Device()
        row=self.capture(d)
        self.assertEqual("observed",row["status"])
        self.assertEqual("emulator_fixture_transport_only",row["scope"])
        self.assertEqual("archived_real_uk_fixture",row["source_kind"])
        self.assertEqual(sha(self.source.read_bytes()),row["source_sha256"])
        self.assertEqual(row["source_sha256"],row["device_installed_sha256"])
        self.assertEqual(row["source_sha256"],row["device_readback_sha256"])
        self.assertEqual(["install","readback","remove"],d.calls)

    def test_wrong_device_installed_digest_is_inconclusive(self):
        row=self.capture(Device(wrong_install=True))
        self.assertEqual("inconclusive",row["status"])
        self.assertEqual("DEVICE_INSTALL_MISMATCH",row["reason_code"])

    def test_corrupt_device_readback_is_inconclusive(self):
        row=self.capture(Device(corrupted_read=True))
        self.assertEqual("inconclusive",row["status"])
        self.assertEqual("DEVICE_READBACK_MISMATCH",row["reason_code"])

    def test_device_exception_fails_closed_and_removes_file(self):
        row=self.capture(Device(fail_read=True))
        self.assertEqual("inconclusive",row["status"])
        self.assertEqual("DEVICE_OPERATION_FAILED",row["reason_code"])

    def test_generated_record_never_becomes_offline_navigation_acceptance(self):
        row=self.capture(Device())
        self.assertNotIn("offline_proof",row)
        self.assertNotIn("mode",row)
        self.assertFalse(row["offline_acceptance"])

    def test_live_emulator_workflow_runs_one_complete_shell_command(self):
        workflow=(ROOT/".github/workflows/test-app.yml").read_text()
        calls=[line.strip() for line in workflow.splitlines()
               if "python3 ../ci/emulator_fixture_readback.py" in line]
        self.assertEqual(1,len(calls))
        self.assertIn("--source ../app-unit/resources/cat3-published-manchester-lanes-3.json",calls[0])
        self.assertIn("--out ../results/emulator-fixture-readback.json",calls[0])
        self.assertIn("results/emulator-fixture-readback.json",workflow)
        self.assertIn("emulator-fixture-readback.json",(ROOT/"ci/process_preflight.py").read_text())


if __name__=="__main__":
    unittest.main()
