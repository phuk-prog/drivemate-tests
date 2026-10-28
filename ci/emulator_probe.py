"""Collect *actual* emulator file and reachability observations.

This is a bounded CI smoke probe, not a download, an isolated application
network namespace, a verified legal road, or offline navigation acceptance.
The caller must not promote it into an offline acceptance record.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


REMOTE = "/data/local/tmp/drivemate-source-smoke.json"


def _call(execute, cmd, *, timeout=20):
    return execute(cmd, capture_output=True, check=False, timeout=timeout)


def _reachable(execute):
    # A successful real baseline proves that ping worked on this emulator.
    # A failed *baseline* cannot be treated as evidence of isolation.
    return _call(execute, ["adb", "shell", "ping", "-c", "1", "-W", "2", "1.1.1.1"]).returncode == 0


def capture_emulator_smoke(fixture, output, execute=subprocess.run):
    fixture = Path(fixture)
    output = Path(output)
    raw = fixture.read_bytes()
    if not raw or len(raw) > 1024 * 1024:
        raise ValueError("EMULATOR_SMOKE_FIXTURE_INVALID")
    fixture_hash = hashlib.sha256(raw).hexdigest()
    if _call(execute, ["adb", "push", str(fixture), REMOTE]).returncode:
        raise ValueError("EMULATOR_FIXTURE_PUSH_FAILED")
    pulled = _call(execute, ["adb", "exec-out", "cat", REMOTE])
    if pulled.returncode:
        raise ValueError("EMULATOR_DEVICE_READBACK_FAILED")
    device_hash = hashlib.sha256(pulled.stdout).hexdigest()
    if device_hash != fixture_hash:
        raise ValueError("EMULATOR_READBACK_MISMATCH")

    baseline = _reachable(execute)
    offline = None
    recovered = None
    isolated = False
    reason = "baseline_unavailable" if not baseline else "not_attempted"
    if baseline:
        try:
            enabled = _call(execute, ["adb", "shell", "cmd", "connectivity", "airplane-mode", "enable"])
            if enabled.returncode != 0:
                reason = "toggle_failed"
            else:
                isolated = True
                # Android emulators may also have Wi-Fi and cellular transports.
                wifi = _call(execute, ["adb", "shell", "svc", "wifi", "disable"])
                data = _call(execute, ["adb", "shell", "svc", "data", "disable"])
                if wifi.returncode or data.returncode:
                    reason = "transport_toggle_failed"
                else:
                    offline = _reachable(execute)
                    reason = "still_reachable" if offline else "probe_blocked"
        finally:
            # Never leave the emulator in airplane mode after a probe, even
            # on an unexpected subprocess/ADB failure.
            disabled = _call(execute, ["adb", "shell", "cmd", "connectivity", "airplane-mode", "disable"])
            _call(execute, ["adb", "shell", "svc", "wifi", "enable"])
            _call(execute, ["adb", "shell", "svc", "data", "enable"])
            recovered = _reachable(execute) if disabled.returncode == 0 else False
            if not recovered and reason == "probe_blocked":
                reason = "network_not_restored"
    network_ok = baseline and isolated and offline is False and recovered is True and reason == "probe_blocked"
    result = {
        "schema": 1,
        "scope": "emulator_smoke_only",
        "probe_method": "adb_ping_icmp_smoke_not_app_connectivity",
        "source": "frozen_public_test_fixture_not_live_release",
        "fixture_sha256": fixture_hash,
        "device_readback_sha256": device_hash,
        "file_integrity_verified": True,
        "baseline_reachable": baseline,
        "isolation_probe_reachable": offline,
        "recovery_reachable": recovered,
        "network_isolation_observed": bool(network_ok),
        "network_reason": "probe_blocked" if network_ok else reason,
        "published_release_download_verified": False,
        "offline_navigation_accepted": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture")
    parser.add_argument("output")
    args = parser.parse_args()
    result = capture_emulator_smoke(args.fixture, args.output)
    print(json.dumps({key: result[key] for key in (
        "scope", "file_integrity_verified", "network_isolation_observed", "network_reason",
        "published_release_download_verified", "offline_navigation_accepted"
    )}, sort_keys=True))
