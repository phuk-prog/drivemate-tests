"""Frozen regressions for genuine *device-executed* emulator network observations.

This suite does not invent source maps or count a network probe as a drive.
"""
import importlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class FakeAdb:
    def __init__(self, probes=(0, 1, 1, 0), failed_control=None):
        self.probes = list(probes)
        self.failed_control = failed_control
        self.commands = []

    def __call__(self, argv):
        self.commands.append(tuple(argv))
        if argv[-6:] == ["toybox", "nc", "-z", "-w", "3", "1.1.1.1"] or False:
            raise AssertionError("probe command must carry port")
        if "nc" in argv:
            if not self.probes:
                raise AssertionError("unexpected repeated probe")
            return subprocess.CompletedProcess(argv, self.probes.pop(0), "", "")
        if self.failed_control and self.failed_control in argv:
            return subprocess.CompletedProcess(argv, 1, "", "unsupported")
        return subprocess.CompletedProcess(argv, 0, "", "")


class AutoEmulatorNetworkProbeTest(unittest.TestCase):
    def setUp(self):
        try:
            self.mod = importlib.import_module("emulator_network_probe")
        except ImportError:
            self.fail("AUTO_EMULATOR_NETWORK_PROBE_MISSING")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.output = Path(self.tmp.name) / "results" / "emulator-network-probe.json"

    def execute(self, probes=(0, 1, 1, 0), failed_control=None):
        adb = FakeAdb(probes, failed_control)
        result = self.mod.run(self.output, execute=adb, settle=lambda: None)
        return result, adb

    def test_real_device_connectivity_is_probed_online_offline_twice_and_recovered(self):
        result, adb = self.execute()
        self.assertEqual("network_isolation_observed", result["status"])
        self.assertEqual("transport_probe_only", result["scope"])
        self.assertEqual(True, result["online_baseline_reachable"])
        self.assertEqual(False, result["before"]["network_reachable"])
        self.assertEqual(False, result["after"]["network_reachable"])
        self.assertEqual(True, result["restored_reachable"])
        self.assertEqual(4, sum("nc" in a for a in adb.commands))
        self.assertTrue(any("airplane-mode" in a and "enable" in a for a in adb.commands))
        self.assertTrue(any("airplane-mode" in a and "disable" in a for a in adb.commands))
        self.assertEqual(result, json.loads(self.output.read_text()))

    def test_failed_online_baseline_is_not_a_successful_isolation(self):
        result, _ = self.execute((1, 1, 1, 0))
        self.assertEqual("inconclusive", result["status"])
        self.assertEqual("NO_ONLINE_BASELINE", result["reason_code"])

    def test_still_reachable_while_offline_is_not_marked_isolated(self):
        result, _ = self.execute((0, 0, 0, 0))
        self.assertEqual("inconclusive", result["status"])
        self.assertEqual("NETWORK_REMAINED_REACHABLE", result["reason_code"])

    def test_one_failed_offline_probe_is_not_enough(self):
        result, _ = self.execute((0, 1, 0, 0))
        self.assertEqual("inconclusive", result["status"])

    def test_recovery_must_be_observed(self):
        result, _ = self.execute((0, 1, 1, 1))
        self.assertEqual("inconclusive", result["status"])
        self.assertEqual("NETWORK_NOT_RESTORED", result["reason_code"])

    def test_failed_control_does_not_count_as_success(self):
        result, adb = self.execute((0, 1, 1, 0), failed_control="airplane-mode")
        self.assertEqual("inconclusive", result["status"])
        self.assertEqual("CONTROL_COMMAND_FAILED", result["reason_code"])
        self.assertTrue(any("disable" in a for a in adb.commands))

    def test_command_timeouts_are_inconclusive_and_always_restore(self):
        adb = FakeAdb()
        def timeout(argv):
            if "nc" in argv:
                raise subprocess.TimeoutExpired(argv, 6)
            return adb(argv)
        result = self.mod.run(self.output, execute=timeout, settle=lambda: None)
        self.assertEqual("inconclusive", result["status"])
        self.assertTrue(any("disable" in a for a in adb.commands))
        self.assertFalse(result.get("offline_navigation_accepted", False))

    def test_workflow_runs_automatic_probe_inside_live_emulator_and_public_output_is_summary_only(self):
        workflow=(Path(__file__).resolve().parents[1] / ".github/workflows/test-app.yml").read_text()
        self.assertIn("python3 ../ci/emulator_network_probe.py", workflow)
        self.assertIn("results/emulator-network-probe.json", workflow)
        self.assertNotIn("results/offline-evidence/", workflow)


if __name__ == "__main__":
    unittest.main()
