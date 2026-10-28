"""API 30 emulator toybox TCP probe compatibility (source-based regression).

The Android 10/11 toybox netcat lacks -z; use -w / -q and a closed stdin.
This tests the *runner*, not whether a particular network is reachable.
"""
import subprocess
import unittest
from unittest.mock import patch
import emulator_network_probe as probe


class EmulatorProbeApi30CompatibilityTest(unittest.TestCase):
    def test_api30_tcp_command_uses_supported_toybox_netcat_options(self):
        self.assertNotIn("-z", probe.TCP, "Android API 30 toybox netcat does not reliably support -z")
        self.assertIn("-w", probe.TCP)
        self.assertIn("-q", probe.TCP)
        self.assertEqual(["1.1.1.1", "443"], probe.TCP[-2:])

    def test_tcp_probe_closes_stdin_so_connection_exits_after_handshake(self):
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess(probe.TCP,0,"","")) as runner:
            probe.execute_adb(probe.TCP)
        self.assertIs(runner.call_args.kwargs.get("stdin"), subprocess.DEVNULL)
        self.assertTrue(runner.call_args.kwargs.get("capture_output"))


if __name__=="__main__":
    unittest.main()
