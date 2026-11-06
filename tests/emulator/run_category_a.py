"""Run Category A emulator checks with bounded, privacy-safe failure evidence."""
import json
import os
import pathlib
import re
import subprocess
import sys

PACKAGE = "com.drivemate.app"
ADB = os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb")
SERIAL = "emulator-5554"
CLASS = "com.drivemate.app.CategoryAEmulatorTest"

PHASES = {
    "activity-ready", "permission-dialog", "permission-denied", "permission-granted",
    "permission-request-ready", "denial-callback", "controlled-provider",
    "tracker-diagnostics", "marker-native-diagnostics", "capture-control",
    "screen-control-verified", "native-control-positive", "native-control-negative",
    "failure-detail", "marker-display", "marker-display-final", "grant-first-fix",
    "grant-native-marker", "native-style-loaded", "marker-good-fix",
    "marker-setting", "weak-state", "exact-accuracy-threshold", "marker-sample",
    "marker-recovered-fix", "reliable-recovery", "gap-baseline",
    "ten-second-warning", "warning-timing", "stale-feed", "gap-recovered-fix",
    "gap-recovered-state", "simulated-movement", "batch-complete", "batch-error",
    "revoked-start", "restored-permission", "restored-fix",
    "restored-native-style", "restored-native-marker", "revocation-complete",
}
PHASES.update(
    f"{prefix}-{index}"
    for prefix in ("marker-good", "marker-weak", "style-reloaded",
                   "marker-reloaded", "marker-threshold", "marker-recovered")
    for index in range(3)
)
CHECK_IDS = PHASES | {
    "screen-control-available", "screen-control-positive",
    "screen-control-negative", "initial-permission", "initial-native-marker",
    "denied-native-marker", "poor-fix-must-not-move-position",
    "warning-before-ten-seconds", "warning-too-late",
    "controlled-feed-must-stop", "warning-message-visible",
    "revoked-permission", "revoked-native-marker", "diagnostic-schema",
}
BOOLEAN_FIELDS = {
    "passed", "expected_weak", "fix_unchanged", "precise", "native_enabled",
    "resumed", "window_focused", "rationale", "services_enabled",
    "authoritative", "reliable", "weak", "tracker_running",
    "has_tracked_fix", "provider_has_fix", "provider_fix_is_mock",
    "provider_fix_matches_injected", "diagnostics_available",
    "provider_enabled", "provider_available", "native_activated",
    "native_has_fix", "native_on_screen", "style_loaded",
    "native_asset_matches_controller", "location_source_present",
    "display_capture_available", "source_location_stale",
    "source_foreground_icon_registered", "source_foreground_stale_icon_registered",
    "foreground_layer_visible", "background_layer_visible",
    "expected_passed", "actual_passed", "control_source_present",
    "control_layer_present", "control_on_screen",
}
NUMBER_FIELDS = {
    "elapsed_ms", "amber_pixels", "delay_ms", "allow_buttons",
    "deny_buttons", "listener_count", "injected_samples",
    "tracked_fix_age_ms", "snapshot_width", "snapshot_height",
    "marker_colored_pixels", "location_layer_count", "visible_location_layers",
    "sprite_amber_pixels", "render_mode", "marker_colored_nearby_pixels",
    "screen_marker_colored_pixels", "location_source_features",
    "rendered_location_features", "rendered_nearby_location_features",
    "screen_amber_pixels", "positive_amber_pixels",
    "negative_amber_pixels", "native_symbol_amber_pixels",
    "foreground_rendered_features", "background_rendered_features",
    "timeout_ms", "expected_pixels_min", "expected_pixels_max",
    "actual_native_pixels", "actual_display_pixels",
    "control_rendered_features",
}
STATUSES = {
    "STOPPED", "WAITING", "GOOGLE", "GPS", "APPROXIMATE",
    "PERMISSION_REQUIRED", "LOCATION_DISABLED", "PROVIDER_UNAVAILABLE",
}
FAILURE_KINDS = {"timeout", "assertion", "runner"}
SAMPLE_PHASES = {"capture-control", "marker-sample", "marker-display"}

# Completion requirements remain unchanged: a blocked check is never a pass.
SUBSYSTEM_REQUIREMENTS = {
    "permissions": {"permission-denied", "permission-granted", "grant-first-fix"},
    "screen_capture": {"screen-control-verified"},
    "native_rendering": (
        {"native-control-positive", "native-control-negative", "marker-display"}
        | {f"{prefix}-{i}" for prefix in
           ("marker-good", "marker-reloaded", "marker-threshold", "marker-recovered")
           for i in range(3)}
    ),
    "gps_accuracy": (
        {"weak-state", "exact-accuracy-threshold", "reliable-recovery"}
        | {f"marker-weak-{i}" for i in range(3)}
    ),
    "warning_timing": {"ten-second-warning", "warning-timing", "stale-feed"},
    "gap_recovery": {"gap-recovered-state", "simulated-movement", "batch-complete"},
    "permission_restoration": {"revoked-start", "restored-permission",
                               "restored-native-marker", "revocation-complete"},
}


def sanitized_record(raw):
    """Fail closed on unexpected fields, types, locations, paths and messages."""
    try:
        value = json.loads(raw) if isinstance(raw, str) else raw
    except (ValueError, TypeError):
        return None
    if type(value) is not dict or type(value.get("phase")) is not str:
        return None
    if value["phase"] not in PHASES:
        return None
    for key, item in value.items():
        if key == "phase":
            continue
        if key in BOOLEAN_FIELDS and type(item) is bool:
            continue
        if key in NUMBER_FIELDS and type(item) is int and 0 <= item <= 10_000_000:
            continue
        if key == "status" and type(item) is str and item in STATUSES:
            continue
        if key == "check_id" and type(item) is str and item in CHECK_IDS:
            continue
        if key == "failure_kind" and type(item) is str and item in FAILURE_KINDS:
            continue
        if key == "error_type" and type(item) is str and item in {
            "AssertionError", "RuntimeException", "SecurityException",
            "IllegalStateException", "IllegalArgumentException",
            "InvocationTargetException", "NullPointerException",
            "TimeoutException",
        }:
            continue
        return None
    return value


def compact_records(records):
    """Keep the first sample, each changed sample, and the explicit final failure."""
    last_samples = {}
    result = []
    for rec in records:
        phase = rec["phase"]
        if phase in SAMPLE_PHASES:
            encoded = json.dumps(rec, sort_keys=True)
            if last_samples.get(phase) == encoded:
                continue
            last_samples[phase] = encoded
        result.append(rec)
    return result


def subsystem_for(check_id):
    if check_id == "diagnostic-schema":
        return "diagnostics"
    if check_id.startswith(("native-control", "marker-good", "marker-reloaded",
                            "marker-recovered", "marker-threshold", "marker-display",
                            "style-reloaded")) or check_id in {
                                "marker-setting", "grant-native-marker",
                                "native-style-loaded", "capture-control",
                            }:
        return "native_rendering"
    if check_id.startswith("screen-control") or check_id == "screen-capture-unavailable":
        return "screen_capture"
    if check_id.startswith("marker-weak") or check_id in {
        "weak-state", "exact-accuracy-threshold", "reliable-recovery",
        "poor-fix-must-not-move-position",
    }:
        return "gps_accuracy"
    if check_id in {"ten-second-warning", "warning-timing", "stale-feed",
                    "warning-before-ten-seconds", "warning-too-late",
                    "warning-message-visible", "controlled-feed-must-stop"}:
        return "warning_timing"
    if check_id in {"gap-recovered-state", "gap-recovered-fix",
                    "simulated-movement", "batch-complete"}:
        return "gap_recovery"
    if check_id.startswith(("revoked-", "restored-", "revocation-")):
        return "permission_restoration"
    if check_id in {"permission-denied", "permission-granted", "grant-first-fix",
                    "denial-callback", "initial-permission", "denied-native-marker"}:
        return "permissions"
    return "unclassified"


def first_failure(records, passed):
    for rec in records:
        if rec["phase"] == "failure-detail":
            return {key: value for key, value in rec.items() if key != "phase"}
    if passed:
        return None
    for rec in records:
        if rec["phase"] == "batch-error" and rec.get("check_id") in CHECK_IDS:
            return {"check_id": rec["check_id"], "failure_kind": "assertion"}
    for rec in records:
        if rec.get("passed") is False and rec["phase"] not in SAMPLE_PHASES:
            return {"check_id": rec["phase"], "failure_kind": "assertion"}
    return {"check_id": "unclassified", "failure_kind": "runner"}


def subsystem_results(records, failure):
    completed = {
        rec["phase"] for rec in records if rec.get("passed") is not False
    }
    failed_group = subsystem_for(failure.get("check_id", "")) if failure else None
    results = {}
    for name, required in SUBSYSTEM_REQUIREMENTS.items():
        if name == failed_group:
            results[name] = {"status": "failed",
                             "check_id": failure["check_id"]}
        elif required <= completed:
            results[name] = {"status": "passed"}
        else:
            results[name] = {
                "status": "blocked",
                "blocked_by": failed_group or "prerequisite_not_completed",
            }
    return results


def adb(label, *args, timeout=45):
    proc = subprocess.run([ADB, "-s", SERIAL, *args],
                          capture_output=True, text=True, timeout=timeout)
    print(json.dumps({"command": label, "return_code": proc.returncode}, sort_keys=True))
    if proc.returncode:
        raise RuntimeError(label)
    return proc.stdout


def run_case(method, required_phases):
    proc = subprocess.run(
        [ADB, "-s", SERIAL, "shell", "am", "instrument", "-w", "-r", "-e",
         "class", f"{CLASS}#{method}",
         PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True, text=True, timeout=600,
    )
    raw = proc.stdout + proc.stderr
    # Raw instrumentation and any unbounded exception text never leave the ephemeral runner.
    pathlib.Path(os.environ["RUNNER_TEMP"], "position-" + method + ".log").write_text(raw)
    accepted = []
    rejected_count = 0
    for line in raw.splitlines():
        prefix = "INSTRUMENTATION_STATUS: category_a="
        if line.startswith(prefix):
            rec = sanitized_record(line[len(prefix):])
            if rec is None:
                rejected_count += 1
            else:
                accepted.append(rec)
    records = compact_records(accepted)
    for rec in records:
        print("Category A diagnostics: " + json.dumps(rec, sort_keys=True))
    totals = re.findall(r"\bOK \((\d+) tests?\)", raw)
    failed_status = re.search(r"INSTRUMENTATION_STATUS_CODE: -[1-4]\b", raw) is not None
    phases = {rec["phase"] for rec in records}
    passed = (
        proc.returncode == 0 and totals == ["1"]
        and not failed_status and required_phases <= phases
        and rejected_count == 0
        and not any(rec.get("passed") is False for rec in records)
    )
    failure = first_failure(records, passed)
    if rejected_count and (failure is None or failure["check_id"] == "unclassified"):
        failure = {"check_id": "diagnostic-schema", "failure_kind": "runner"}
    summary = {
        "case": method, "passed": passed, "diagnostic_records": len(records),
        "rejected_diagnostic_records": rejected_count,
        "missing_phases": sorted(required_phases - phases),
        "first_failure": failure,
    }
    print(json.dumps(summary, sort_keys=True))
    return records, summary


def self_test():
    valid = {
        "phase": "failure-detail", "check_id": "native-control-positive",
        "failure_kind": "timeout", "expected_pixels_min": 101,
        "actual_native_pixels": 0, "actual_display_pixels": 0,
        "timeout_ms": 10_000, "elapsed_ms": 10_112,
        "control_source_present": True, "control_layer_present": True,
        "control_on_screen": False, "control_rendered_features": 0,
    }
    assert sanitized_record(valid) == valid
    assert sanitized_record({**valid, "latitude": 53.4}) is None
    assert sanitized_record({**valid, "stack_trace": "private"}) is None
    assert subsystem_for("diagnostic-schema") == "diagnostics"
    assert sanitized_record({**valid, "check_id": "secret/path"}) is None
    assert sanitized_record({**valid, "actual_native_pixels": -1}) is None
    base = [
        {"phase": "permission-denied", "passed": True},
        {"phase": "permission-granted", "passed": True},
        {"phase": "grant-first-fix", "passed": True},
        {"phase": "screen-control-verified", "passed": True},
        {"phase": "capture-control", "native_symbol_amber_pixels": 0},
        {"phase": "capture-control", "native_symbol_amber_pixels": 0},
        valid,
    ]
    compacted = compact_records(base)
    assert len(compacted) == len(base) - 1
    found = first_failure(compacted, False)
    assert found == {key: value for key, value in valid.items() if key != "phase"}
    results = subsystem_results(compacted, found)
    assert results["permissions"]["status"] == "passed"
    assert results["screen_capture"]["status"] == "passed"
    assert results["native_rendering"]["status"] == "failed"
    assert results["gps_accuracy"] == {
        "status": "blocked", "blocked_by": "native_rendering"
    }
    assert results["warning_timing"]["status"] == "blocked"
    assert first_failure([{"phase": "batch-complete", "passed": True}], True) is None
    print(json.dumps({"diagnostic_contract_passed": True, "checks": 12},
                     sort_keys=True))


def main():
    report = {
        "app_sha": os.environ["APP_SHA"],
        "harness_sha": os.environ["GITHUB_SHA"],
        "run_id": os.environ["GITHUB_RUN_ID"],
        "verification_run_id": os.environ["VERIFICATION_RUN_ID"],
        "api_level": 28, "app_installed": False, "app_acceptance": False,
        "status": "failed", "tests": 0, "failures": 1, "errors": 0,
        "skipped": 0, "diagnostic_records": 0,
        "rejected_diagnostic_records": 0, "first_failure": None,
        "apk_published": False, "raw_logs_published": False,
        "checks": ["permission-denial-grant", "permission-revocation-restoration",
                   "normal-marker-visibility", "no-weak-marker",
                   "native-style-reload", "accuracy-boundary",
                   "ten-second-warning", "gap-recovery"],
    }
    all_records = []
    try:
        adb("install-app", "install", "-r", os.environ["APK"], timeout=120)
        adb("install-instrumentation", "install", "-r", os.environ["TEST_APK"], timeout=120)
        report["app_installed"] = True
        adb("clear-disposable-app", "shell", "pm", "clear", PACKAGE)
        adb("allow-controlled-provider", "shell", "appops", "set",
            PACKAGE, "android:mock_location", "allow")
        required = {
            "permission-denied", "permission-granted", "grant-native-marker",
            "screen-control-verified", "native-control-positive",
            "native-control-negative", "capture-control", "marker-display",
            "warning-timing", "stale-feed", "gap-recovered-state",
            "simulated-movement", "batch-complete",
        }
        required.update(
            f"{prefix}-{index}"
            for prefix in ("marker-good", "marker-weak", "marker-reloaded",
                           "marker-threshold", "marker-recovered")
            for index in range(3)
        )
        records, first = run_case("permissionMarkersWarningAndGapRecovery", required)
        all_records.extend(records)
        report["tests"] = 1
        report["diagnostic_records"] += first["diagnostic_records"]
        report["rejected_diagnostic_records"] += first["rejected_diagnostic_records"]
        report["first_failure"] = first["first_failure"]
        if not first["passed"]:
            raise AssertionError("primary-acceptance")
        # Revocation belongs between invocations: Android terminates the app on revoke.
        adb("stop-before-revoke", "shell", "am", "force-stop", PACKAGE)
        for permission in ("android.permission.ACCESS_FINE_LOCATION",
                           "android.permission.ACCESS_COARSE_LOCATION"):
            adb("revoke-location", "shell", "pm", "revoke", PACKAGE, permission)
        records, second = run_case("revokedPermissionThenRestoredCallback", {
            "revoked-start", "restored-permission", "restored-fix",
            "restored-native-marker", "revocation-complete",
        })
        all_records.extend(records)
        report["tests"] = 2
        report["diagnostic_records"] += second["diagnostic_records"]
        report["rejected_diagnostic_records"] += second["rejected_diagnostic_records"]
        report["first_failure"] = second["first_failure"]
        if not second["passed"]:
            raise AssertionError("restoration-acceptance")
        report.update({"status": "passed", "app_acceptance": True, "failures": 0})
    except Exception as error:
        if report["first_failure"] is None:
            report["first_failure"] = {
                "check_id": "runner-infrastructure", "failure_kind": "runner"
            }
        print("::error::Category A failed at " +
              report["first_failure"]["check_id"] +
              "; inspect sanitised failure artifact (raw output withheld)")
    finally:
        report["subsystems"] = subsystem_results(all_records, report["first_failure"])
        output = pathlib.Path("public-results/category-a-emulator.json")
        output.parent.mkdir(exist_ok=True)
        output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
        print(json.dumps({
            "status": report["status"],
            "first_failure": report["first_failure"],
            "subsystems": report["subsystems"],
            "sanitized_result": str(output),
        }, sort_keys=True))
    if report["status"] != "passed":
        raise SystemExit(1)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif not sys.argv[1:]:
        main()
    else:
        raise SystemExit("Unsupported argument")
