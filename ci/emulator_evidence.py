"""Automatic ADB *environment smoke* receipts; not offline-navigation acceptance.

Uses an archived, real-UK source fixture solely to check emulator file transport
and readback. No real published-release download, production map cache, route,
legal road truth, or installed user APK is tested here.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


class AndroidAdbDevice:
    REMOTE = "/data/local/tmp/drivemate-emulator-fixture.json"

    def _adb(self, *args, timeout=15, check=True):
        result = subprocess.run(["adb", *args], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode:
            # Do not echo unbounded device output or private paths.
            raise OSError("ADB_COMMAND_FAILED")
        return result

    def probe(self):
        # Do not infer offline from airplane mode or an absent Wi-Fi icon.
        # At least one direct network check must work before isolation.
        checks = (
            ("shell", "ping", "-c", "1", "-W", "2", "8.8.8.8"),
            ("shell", "ping", "-c", "1", "-W", "2", "1.1.1.1"),
            ("shell", "toybox", "nc", "-z", "-w", "2", "1.1.1.1", "443"),
        )
        for command in checks:
            try:
                if self._adb(*command, timeout=6, check=False).returncode == 0:
                    return True
            except (subprocess.TimeoutExpired, OSError):
                continue
        return False

    def install(self, data):
        with tempfile.NamedTemporaryFile(prefix="dmn-adb-fixture-", suffix=".json") as tmp:
            tmp.write(data)
            tmp.flush()
            self._adb("push", tmp.name, self.REMOTE, timeout=25)
        output = self._adb("shell", "sha256sum", self.REMOTE).stdout.decode("ascii", "replace")
        match = re.match(r"^([0-9a-f]{64})\s", output)
        if not match:
            raise OSError("ADB_DIGEST_UNAVAILABLE")
        return match.group(1)

    def isolate(self):
        self._adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        self._adb("shell", "svc", "wifi", "disable")
        self._adb("shell", "svc", "data", "disable")

    def readback(self):
        return self._adb("exec-out", "cat", self.REMOTE, timeout=15).stdout

    def restore(self):
        # Always attempt the restore, even if one individual command failed.
        actions = (
            ("shell", "cmd", "connectivity", "airplane-mode", "disable"),
            ("shell", "svc", "wifi", "enable"),
            ("shell", "svc", "data", "enable"),
            ("shell", "rm", "-f", self.REMOTE),
        )
        failures = 0
        for action in actions:
            try:
                if self._adb(*action, check=False).returncode:
                    failures += 1
            except (OSError, subprocess.TimeoutExpired):
                failures += 1
        if failures:
            raise OSError("ADB_RESTORE_FAILED")


def capture(source_file, output_file, device):
    """Return a bounded receipt even when device evidence is inconclusive."""
    data = Path(source_file).read_bytes()
    digest = sha(data)
    result = {
        "schema": 1, "scope": "emulator_environment_smoke_only",
        "source_kind": "archived_real_uk_fixture",
        "status": "inconclusive", "reason_code": "EMULATOR_EVIDENCE_COMMAND_FAILED",
        "source_sha256": digest, "installed_sha256": None,
        "readback_sha256": None,
        "network": {
            "method": "adb_direct_reachability",
            "online_before": None,
            "reachable_isolated_before_readback": None,
            "reachable_isolated_after_readback": None,
        },
        "published_release_downloaded": False,
        "navigation_tested": False,
        "offline_acceptance": False,
    }
    try:
        result["network"]["online_before"] = bool(device.probe())
        result["installed_sha256"] = device.install(data)
        device.isolate()
        result["network"]["reachable_isolated_before_readback"] = bool(device.probe())
        result["readback_sha256"] = sha(device.readback())
        result["network"]["reachable_isolated_after_readback"] = bool(device.probe())
        if result["installed_sha256"] != digest or result["readback_sha256"] != digest:
            result["reason_code"] = "READBACK_DIGEST_MISMATCH"
        elif not result["network"]["online_before"]:
            result["reason_code"] = "BASELINE_UNVERIFIED"
        elif (result["network"]["reachable_isolated_before_readback"] or
              result["network"]["reachable_isolated_after_readback"]):
            result["reason_code"] = "NETWORK_REACHABLE_WHILE_ISOLATED"
        else:
            result["status"] = "observed"
            result["reason_code"] = None
    except (OSError, subprocess.TimeoutExpired, ValueError):
        result["status"] = "inconclusive"
        result["reason_code"] = "EMULATOR_EVIDENCE_COMMAND_FAILED"
    finally:
        try:
            device.restore()
        except (OSError, subprocess.TimeoutExpired):
            result["status"] = "inconclusive"
            result["reason_code"] = "RESTORE_FAILED"
        destination = Path(output_file)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    result = capture(args.source, args.out, AndroidAdbDevice())
    # Inconclusive network probes are not silently promoted to successful
    # isolation, but this optional environment smoke is not the release gate.
    print("emulator environment evidence: " + result["status"] +
          (" (" + result["reason_code"] + ")" if result["reason_code"] else ""))
