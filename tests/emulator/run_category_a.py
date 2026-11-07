#!/usr/bin/env python3
"""Category A basic emulator check: runs one instrumented test and publishes sanitised pass or fail only."""
import json
import os
import pathlib
import subprocess
import sys

ADB = os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb")
SERIAL = "emulator-5554"
PACKAGE = "com.drivemate.app"
CASES = [("com.drivemate.app.CategoryAEmulatorTest", "categoryABasics"),
         ("com.drivemate.app.PlainMapTest", "plainMapPaintsKnownSymbol")]
REQUIRED = [
    "controlled-provider", "permission-denied", "permission-granted",
    "grant-first-fix", "grant-native-marker", "native-control-positive",
    "plain-map-activity", "plain-map-style", "plain-map-paints",
]
PHASES = set(REQUIRED) | {"activity-ready", "permission-request-ready", "failure-detail"}
BOOLEAN_FIELDS = {"passed"}
NUMBER_FIELDS = {"elapsed_ms", "timeout_ms"}
FAILURE_KINDS = {"timeout", "assertion"}


def sanitized_record(raw):
    """Keep only known phases and fields with safe types; anything else is dropped."""
    try:
        value = json.loads(raw) if isinstance(raw, str) else raw
    except (ValueError, TypeError):
        return None
    if type(value) is not dict or value.get("phase") not in PHASES:
        return None
    for key, item in value.items():
        if key == "phase":
            continue
        if key in BOOLEAN_FIELDS and type(item) is bool:
            continue
        if key in NUMBER_FIELDS and type(item) is int and 0 <= item <= 10_000_000:
            continue
        if key == "check_id" and item in PHASES:
            continue
        if key == "failure_kind" and item in FAILURE_KINDS:
            continue
        return None
    return value


def self_test():
    assert sanitized_record({"phase": "permission-granted", "passed": True, "elapsed_ms": 5}) is not None
    assert sanitized_record({"phase": "permission-granted", "private": "secret"}) is None
    assert sanitized_record({"phase": "secret-phase", "passed": True}) is None
    assert sanitized_record({"phase": "failure-detail", "failure_kind": "timeout", "timeout_ms": 15000}) is not None
    print(json.dumps({"self_test": "passed"}, sort_keys=True))


def run_case(class_name, method):
    proc = subprocess.run(
        [ADB, "-s", SERIAL, "shell", "am", "instrument", "-w", "-r", "-e", "class", f"{class_name}#{method}",
         PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True, text=True, timeout=600,
    )
    # Raw instrumentation output stays in the ephemeral runner; only sanitised records are kept.
    pathlib.Path(os.environ["RUNNER_TEMP"], "position-" + method + ".log").write_text(proc.stdout + proc.stderr)
    records = []
    prefix = "INSTRUMENTATION_STATUS: category_a="
    for line in (proc.stdout + proc.stderr).splitlines():
        if line.startswith(prefix):
            record = sanitized_record(line[len(prefix):])
            if record is not None:
                records.append(record)
    return records


def run_basics():
    records = []
    for class_name, method in CASES:
        records.extend(run_case(class_name, method))
    return records


def main():
    report = {
        "app_sha": os.environ["APP_SHA"],
        "harness_sha": os.environ["GITHUB_SHA"],
        "run_id": os.environ["GITHUB_RUN_ID"],
        "mode": "basics",
        "status": "failed",
        "passed_phases": [],
        "first_failure": None,
        "apk_published": False,
        "raw_logs_published": False,
    }
    try:
        records = run_basics()
        passed = {r["phase"] for r in records if r.get("passed") is True}
        report["passed_phases"] = sorted(passed & set(REQUIRED))
        report["steps"] = [{"step": phase, "result": "passed" if phase in passed else "not passed"} for phase in REQUIRED]
        failures = [r for r in records if r["phase"] == "failure-detail"]
        if failures:
            report["first_failure"] = {k: v for k, v in failures[0].items() if k != "phase"}
        missing = [phase for phase in REQUIRED if phase not in passed]
        if missing:
            report["first_failure"] = report["first_failure"] or {"missing_phase": missing[0]}
            raise AssertionError("basics")
        report["status"] = "passed"
    except Exception:
        print("::error::Category A basic check failed; raw output withheld")
    finally:
        output = pathlib.Path("public-results/category-a-emulator.json")
        output.parent.mkdir(exist_ok=True)
        output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
        for step in report["steps"]:
            print("Category A basic step: " + step["step"] + " -> " + step["result"])
        print(json.dumps({"status": report["status"], "first_failure": report["first_failure"]}, sort_keys=True))
    if report["status"] != "passed":
        raise SystemExit(1)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif not sys.argv[1:]:
        main()
    else:
        raise SystemExit("Unsupported argument")
