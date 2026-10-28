#!/usr/bin/env python3
"""Observe emulator TCP connectivity; never infer actual offline navigation.

Run while the Android emulator is alive. All TCP probes execute *on the
device* via adb shell toybox nc, not on the GitHub Actions host.
Airplane mode/wifi/data commands are controls, not evidence: disconnected
TCP must also be observed after a successful online baseline, twice during
the isolated phase, and restored afterwards.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time


TCP = ["adb", "shell", "toybox", "nc", "-w", "3", "-q", "1", "1.1.1.1", "443"]
AIRPLANE_ON = ["adb", "shell", "cmd", "connectivity", "airplane-mode", "enable"]
AIRPLANE_OFF = ["adb", "shell", "cmd", "connectivity", "airplane-mode", "disable"]
WIFI_OFF = ["adb", "shell", "svc", "wifi", "disable"]
WIFI_ON = ["adb", "shell", "svc", "wifi", "enable"]
DATA_OFF = ["adb", "shell", "svc", "data", "disable"]
DATA_ON = ["adb", "shell", "svc", "data", "enable"]


def execute_adb(argv):
    return subprocess.run(argv, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=9, check=False)


def run(output, *, execute=execute_adb, settle=lambda: time.sleep(2)):
    """Capture real ADB observations, never synthesize a success verdict.

    execute/settle are replaceable ONLY for Python contract unit tests.
    Production CLI uses the subprocess-backed defaults unconditionally.
    """
    output = Path(output)
    probes = []
    controls_ok = True
    def command(argv):
        try:
            r = execute(list(argv))
            if r.returncode is None:
                return None
            # Reject unsupported command/option as UNKNOWN, not an apparent
            # disconnected socket.
            stderr = ((r.stderr or "") + " " + (r.stdout or "")).lower()
            if any(word in stderr for word in
                   ("not found", "unknown option", "invalid option", "usage:", "permission denied")):
                return None
            return r.returncode == 0
        except (OSError, subprocess.TimeoutExpired):
            return None

    def probe():
        value = command(TCP)
        probes.append(value)
        return value

    online = probe()
    try:
        for command_args in (AIRPLANE_ON, WIFI_OFF, DATA_OFF):
            if command(command_args) is not True:
                controls_ok = False
        settle()
        before = probe()
        settle()
        after = probe()
    finally:
        # Always attempt recovery, including exceptions in probe/control logic.
        for command_args in (AIRPLANE_OFF, WIFI_ON, DATA_ON):
            if command(command_args) is not True:
                controls_ok = False
        settle()
        restored = probe()

    if not controls_ok:
        reason = "CONTROL_COMMAND_FAILED"
    elif online is None:
        reason = "PROBE_UNAVAILABLE"
    elif online is not True:
        reason = "NO_ONLINE_BASELINE"
    elif before is None or after is None:
        reason = "PROBE_UNAVAILABLE"
    elif before or after:
        reason = "NETWORK_REMAINED_REACHABLE"
    elif restored is not True:
        reason = "NETWORK_NOT_RESTORED"
    else:
        reason = "NETWORK_BLOCK_OBSERVED"
    verified = reason == "NETWORK_BLOCK_OBSERVED"
    record = {
        "schema": 1, "case_id": "emulator-network-transport-only",
        "method": "adb_with_reachability_probe",
        "scope": "transport_probe_only",
        "status": "network_isolation_observed" if verified else "inconclusive",
        "reason_code": reason,
        "online_baseline_reachable": online,
        "before": {"network_reachable": before},
        "after": {"network_reachable": after},
        "restored_reachable": restored,
        "probe_count": len(probes),
        "asset_install_verified": False,
        "navigation_offline_accepted": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(record, sort_keys=True, indent=2)+"\n")
    return record


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    options=parser.parse_args()
    report=run(options.output)
    print(json.dumps({
        "emulator_network_probe": report["status"],
        "reason_code": report["reason_code"],
        "scope": report["scope"],
    }, sort_keys=True))
