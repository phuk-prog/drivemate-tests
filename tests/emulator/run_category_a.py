"""Run the complete emulator acceptance batch and print only allowlisted diagnostics."""
import json
import os
import pathlib
import re
import subprocess

PACKAGE = "com.drivemate.app"
ADB = os.path.join(os.environ["ANDROID_HOME"], "platform-tools", "adb")
SERIAL = "emulator-5554"
CLASS = "com.drivemate.app.CategoryAEmulatorTest"
PHASES = {
    "activity-ready", "permission-dialog", "permission-denied", "permission-granted",
    "permission-request-ready", "denial-callback",
    "controlled-provider", "tracker-diagnostics",
    "marker-native-diagnostics", "capture-control", "marker-display", "marker-display-final",
    "grant-first-fix", "grant-native-marker", "native-style-loaded", "marker-good-fix",
    "marker-setting", "weak-state", "exact-accuracy-threshold", "marker-sample",
    "marker-recovered-fix", "reliable-recovery", "gap-baseline", "ten-second-warning",
    "warning-timing", "stale-feed", "gap-recovered-fix", "gap-recovered-state",
    "simulated-movement", "batch-complete", "batch-error", "revoked-start",
    "restored-permission", "restored-fix", "restored-native-style", "restored-native-marker",
    "revocation-complete",
}
PHASES.update(f"{prefix}-{index}" for prefix in (
    "marker-good", "marker-weak", "style-reloaded", "marker-reloaded", "marker-threshold", "marker-recovered"
) for index in range(3))
BOOLEAN_FIELDS = {"passed", "expected_weak", "fix_unchanged", "precise", "native_enabled",
                  "resumed", "window_focused", "rationale", "services_enabled", "authoritative", "reliable", "weak",
                  "tracker_running", "has_tracked_fix", "provider_has_fix", "provider_fix_is_mock",
                  "provider_fix_matches_injected", "diagnostics_available", "provider_enabled", "provider_available"}
BOOLEAN_FIELDS.update({"native_activated", "native_has_fix", "native_on_screen", "style_loaded",
                       "native_asset_matches_controller", "location_source_present",
                       "display_capture_available", "source_location_stale",
                       "source_foreground_icon_registered", "source_foreground_stale_icon_registered",
                       "foreground_layer_visible", "background_layer_visible"})
NUMBER_FIELDS = {"elapsed_ms", "amber_pixels", "delay_ms", "allow_buttons", "deny_buttons",
                 "listener_count", "injected_samples", "tracked_fix_age_ms"}
NUMBER_FIELDS.update({"snapshot_width", "snapshot_height", "marker_colored_pixels", "location_layer_count",
                      "visible_location_layers", "sprite_amber_pixels", "render_mode",
                      "marker_colored_nearby_pixels", "screen_marker_colored_pixels",
                      "location_source_features", "rendered_location_features",
                      "rendered_nearby_location_features", "screen_amber_pixels",
                       "positive_amber_pixels", "negative_amber_pixels",
                       "native_symbol_amber_pixels", "foreground_rendered_features",
                       "background_rendered_features"})
STATUSES = {"STOPPED", "WAITING", "GOOGLE", "GPS", "APPROXIMATE", "PERMISSION_REQUIRED",
            "LOCATION_DISABLED", "PROVIDER_UNAVAILABLE"}


def sanitized_record(raw):
    try:
        value = json.loads(raw)
    except (ValueError, TypeError):
        return None
    if type(value) is not dict or type(value.get("phase")) is not str or value["phase"] not in PHASES:
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
        if key == "error_type" and type(item) is str and item in {"AssertionError", "RuntimeException", "SecurityException",
                "IllegalStateException", "IllegalArgumentException", "InvocationTargetException",
                "NullPointerException", "TimeoutException"}:
            continue
        return None
    return value


def adb(label, *args, timeout=45):
    result = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout)
    print(json.dumps({"command": label, "return_code": result.returncode}, sort_keys=True))
    if result.returncode:
        raise RuntimeError(label)
    return result.stdout


def run_case(method, required_phases):
    result = subprocess.run([
        ADB, "-s", SERIAL, "shell", "am", "instrument", "-w", "-r", "-e", "class", f"{CLASS}#{method}",
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner",
    ], capture_output=True, text=True, timeout=600)
    raw = result.stdout + result.stderr
    # Retain raw output only in the private runner directory, deleted by the workflow's always step.
    pathlib.Path(os.environ["RUNNER_TEMP"], "position-" + method + ".log").write_text(raw)
    records = []
    for line in raw.splitlines():
        prefix = "INSTRUMENTATION_STATUS: category_a="
        if line.startswith(prefix):
            record = sanitized_record(line[len(prefix):])
            if record is not None:
                records.append(record)
                print("Category A diagnostics: " + json.dumps(record, sort_keys=True))
    totals = re.findall(r"\bOK \((\d+) tests?\)", raw)
    failed_status = re.search(r"INSTRUMENTATION_STATUS_CODE: -[1-4]\b", raw) is not None
    phases = {record["phase"] for record in records}
    passed = result.returncode == 0 and totals == ["1"] and not failed_status and required_phases <= phases
    print(json.dumps({"case": method, "passed": passed, "diagnostic_records": len(records),
                      "missing_phases": sorted(required_phases - phases)}, sort_keys=True))
    if not passed:
        raise AssertionError("emulator-batch-case")
    return len(records)


def main():
    adb("install-app", "install", "-r", os.environ["APK"], timeout=120)
    adb("install-instrumentation", "install", "-r", os.environ["TEST_APK"], timeout=120)
    adb("clear-disposable-app", "shell", "pm", "clear", PACKAGE)
    adb("allow-controlled-provider", "shell", "appops", "set", PACKAGE, "android:mock_location", "allow")
    required = {"permission-denied", "permission-granted", "grant-native-marker", "capture-control", "marker-display", "warning-timing",
                "stale-feed", "gap-recovered-state", "simulated-movement", "batch-complete"}
    required.update(f"{prefix}-{index}" for prefix in (
        "marker-good", "marker-weak", "marker-reloaded", "marker-threshold", "marker-recovered"
    ) for index in range(3))
    diagnostics = run_case("permissionMarkersWarningAndGapRecovery", required)
    # Android kills a process when its runtime grant is revoked, so revoke between invocations.
    adb("stop-before-revoke", "shell", "am", "force-stop", PACKAGE)
    for permission in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"):
        adb("revoke-location", "shell", "pm", "revoke", PACKAGE, permission)
    diagnostics += run_case("revokedPermissionThenRestoredCallback", {
        "revoked-start", "restored-permission", "restored-fix", "restored-native-marker", "revocation-complete",
    })
    result = {"app_sha": os.environ["APP_SHA"], "harness_sha": os.environ["GITHUB_SHA"],
              "run_id": os.environ["GITHUB_RUN_ID"], "verification_run_id": os.environ["VERIFICATION_RUN_ID"],
              "api_level": 28, "app_installed": True, "app_acceptance": True,
              "tests": 2, "failures": 0, "errors": 0, "skipped": 0,
              "diagnostic_records": diagnostics, "apk_published": False,
              "checks": ["permission-denial-grant", "permission-revocation-restoration", "normal-marker-visibility", "no-weak-marker",
                         "native-style-reload", "accuracy-boundary", "ten-second-warning", "gap-recovery"]}
    output = pathlib.Path("public-results/category-a-emulator.json")
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("::error::Category A emulator batch failed: " + type(error).__name__ + "; raw output withheld")
        raise SystemExit(1)
