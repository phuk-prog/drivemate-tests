"""Frozen regression expectations for real emulator-generated *smoke* evidence.

This does not claim published offline map delivery or network-isolated navigation.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "ci/emulator_probe.py"


class FakeAdb:
    def __init__(self, *, offline_reachable=False, readback_corrupt=False, baseline_reachable=True):
        self.commands = []
        self.offline_reachable=offline_reachable
        self.readback_corrupt=readback_corrupt
        self.baseline_reachable=baseline_reachable
        self.isolated=False
        self.file=None

    def __call__(self, argv, **kwargs):
        import subprocess
        self.commands.append(tuple(argv))
        cmd = " ".join(argv)
        def result(code=0, payload=b""):
            return subprocess.CompletedProcess(argv, code, payload, b"")
        if "push" in argv:
            self.file=Path(argv[2]).read_bytes()
            return result()
        if "exec-out" in argv and "cat" in argv:
            return result(0,(self.file or b"")+ (b"CORRUPT" if self.readback_corrupt else b""))
        if "ping" in argv:
            reachable = self.offline_reachable if self.isolated else self.baseline_reachable
            return result(0 if reachable else 1)
        if "airplane-mode enable" in cmd:
            self.isolated=True
        if "airplane-mode disable" in cmd:
            self.isolated=False
        return result()


class EmulatorEvidenceRunnerTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(PROBE.exists(), "EMULATOR_PROBE_RUNNER_MISSING")
        spec=importlib.util.spec_from_file_location("emulator_probe", PROBE)
        self.module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.fixture=Path(self.tmp.name,"source.json")
        self.fixture.write_bytes(b'{"source":"bounded real-source fixture"}')
        self.out=Path(self.tmp.name,"evidence.json")

    def capture(self, fake):
        self.module.capture_emulator_smoke(self.fixture, self.out, execute=fake)
        return json.loads(self.out.read_text())

    def test_runner_captures_device_readback_and_restores_network(self):
        fake=FakeAdb()
        r=self.capture(fake)
        self.assertEqual("emulator_smoke_only",r["scope"])
        self.assertEqual(hashlib.sha256(self.fixture.read_bytes()).hexdigest(),r["device_readback_sha256"])
        self.assertTrue(r["file_integrity_verified"])
        self.assertTrue(r["network_isolation_observed"])
        self.assertTrue(any("airplane-mode disable" in " ".join(c) for c in fake.commands))
        self.assertFalse(fake.isolated)
        self.assertFalse(r["published_release_download_verified"])
        self.assertFalse(r["offline_navigation_accepted"])

    def test_probe_fails_closed_when_baseline_network_is_unavailable(self):
        r=self.capture(FakeAdb(baseline_reachable=False))
        self.assertFalse(r["network_isolation_observed"])
        self.assertEqual("baseline_unavailable",r["network_reason"])

    def test_probe_fails_closed_if_network_stays_reachable_while_isolated(self):
        r=self.capture(FakeAdb(offline_reachable=True))
        self.assertFalse(r["network_isolation_observed"])
        self.assertEqual("still_reachable",r["network_reason"])

    def test_modified_readback_rejected_and_isolation_restored(self):
        fake=FakeAdb(readback_corrupt=True)
        with self.assertRaisesRegex(ValueError,"EMULATOR_READBACK_MISMATCH"):
            self.capture(fake)
        self.assertFalse(fake.isolated)


if __name__=="__main__":
    unittest.main()
