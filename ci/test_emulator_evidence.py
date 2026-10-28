"""Tests for a genuine ADB-generated environment probe, not navigation acceptance.

Fake devices simulate only the test tool's errors, never real UK road layouts.
The production emulator run must generate its own evidence before any result
is called observed.
"""
import hashlib
import importlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


class FakeDevice:
    def __init__(self, online=True, isolation_works=True, corrupt=False, read_error=False):
        self.online = online
        self.isolation_works = isolation_works
        self.corrupt = corrupt
        self.read_error = read_error
        self.isolated = False
        self.restored = False
        self.bytes = b""
        self.calls = []

    def probe(self):
        self.calls.append("probe")
        return self.online if not self.isolated else (False if self.isolation_works else True)

    def install(self, data):
        self.calls.append("install")
        self.bytes = data
        return hashlib.sha256(data).hexdigest()

    def isolate(self):
        self.calls.append("isolate")
        self.isolated = True

    def readback(self):
        self.calls.append("readback")
        if self.read_error:
            raise OSError("device read failed")
        return self.bytes + (b"tampered" if self.corrupt else b"")

    def restore(self):
        self.calls.append("restore")
        self.restored = True
        self.isolated = False


class AutomaticEmulatorEvidenceTest(unittest.TestCase):
    def setUp(self):
        module = importlib.import_module("emulator_evidence") if (ROOT/"ci/emulator_evidence.py").exists() else None
        self.assertIsNotNone(module, "EMULATOR_EVIDENCE_GENERATOR_MISSING")
        self.capture = module.capture
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)/"real-uk-source-fixture.json"
        self.source.write_bytes(b'{"source":"previously archived UK dataset"}')
        self.output = Path(self.tmp.name)/"emulator-evidence-smoke.json"

    def run_capture(self, device):
        record = self.capture(self.source, self.output, device)
        self.assertTrue(self.output.is_file())
        self.assertEqual(record, json.loads(self.output.read_text()))
        self.assertTrue(device.restored, "Network controls must always be restored")
        return record

    def test_device_generated_file_readback_and_isolation_observed(self):
        d = FakeDevice()
        r = self.run_capture(d)
        self.assertEqual("observed", r["status"])
        self.assertEqual("emulator_environment_smoke_only", r["scope"])
        self.assertEqual("archived_real_uk_fixture", r["source_kind"])
        self.assertFalse(r["published_release_downloaded"])
        self.assertFalse(r["navigation_tested"])
        self.assertFalse(r["offline_acceptance"])
        self.assertTrue(r["network"]["online_before"])
        self.assertFalse(r["network"]["reachable_isolated_before_readback"])
        self.assertFalse(r["network"]["reachable_isolated_after_readback"])
        self.assertEqual(hashlib.sha256(self.source.read_bytes()).hexdigest(), r["installed_sha256"])
        self.assertEqual(r["source_sha256"], r["readback_sha256"])
        self.assertLess(d.calls.index("isolate"), d.calls.index("readback"))

    def test_no_reachable_baseline_is_inconclusive_not_offline(self):
        r = self.run_capture(FakeDevice(online=False))
        self.assertEqual("inconclusive", r["status"])
        self.assertEqual("BASELINE_UNVERIFIED", r["reason_code"])
        self.assertFalse(r["offline_acceptance"])

    def test_isolation_failed_is_visible_not_pass(self):
        r = self.run_capture(FakeDevice(isolation_works=False))
        self.assertEqual("inconclusive", r["status"])
        self.assertEqual("NETWORK_REACHABLE_WHILE_ISOLATED", r["reason_code"])

    def test_device_readback_tampering_is_not_accepted(self):
        r = self.run_capture(FakeDevice(corrupt=True))
        self.assertEqual("inconclusive", r["status"])
        self.assertEqual("READBACK_DIGEST_MISMATCH", r["reason_code"])

    def test_device_exception_is_recorded_and_restored(self):
        r = self.run_capture(FakeDevice(read_error=True))
        self.assertEqual("inconclusive", r["status"])
        self.assertEqual("EMULATOR_EVIDENCE_COMMAND_FAILED", r["reason_code"])

    def test_no_published_source_or_real_navigation_claim_can_be_inferred(self):
        r = self.run_capture(FakeDevice())
        self.assertNotIn("offline_proof", r)
        self.assertNotIn("mode", r)
        self.assertEqual("archived_real_uk_fixture", r["source_kind"])
        self.assertFalse(r["offline_acceptance"])

    def test_live_emulator_workflow_generates_and_publishes_bounded_summary(self):
        workflow = (ROOT/".github/workflows/test-app.yml").read_text()
        self.assertIn("ci/emulator_evidence.py", workflow)
        self.assertIn("results/emulator-evidence-smoke.json", workflow)
        self.assertIn("emulator-evidence-smoke.json", (ROOT/"ci/process_preflight.py").read_text())


if __name__ == "__main__":
    unittest.main()
