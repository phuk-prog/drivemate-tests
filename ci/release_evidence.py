"""Validate exact-revision public test evidence. Never build or sign an APK here."""
import json
import re
import sys
from pathlib import Path


def validate(summary, app_sha, tests_sha):
    for revision in (app_sha, tests_sha):
        if not re.fullmatch(r"[0-9a-f]{40}", revision):
            raise ValueError("Expected an immutable full commit SHA")
    if summary.get("app_commit") != app_sha:
        raise ValueError("Test evidence app SHA mismatch")
    if summary.get("public_tests_sha") != tests_sha or summary.get("drivemate_tests_sha") != tests_sha:
        raise ValueError("Test evidence suite SHA mismatch; stale suite cannot qualify")
    total, passed = summary.get("total"), summary.get("passed")
    if type(total) is not int or total <= 0 or type(passed) is not int or passed != total:
        raise ValueError("Every expected test must pass; empty or incomplete suite")
    tests = summary.get("tests")
    if not isinstance(tests, list) or len(tests) != total:
        raise ValueError("Test evidence denominator mismatch")
    if any(not isinstance(t, dict) or t.get("status") != "passed" for t in tests):
        raise ValueError("Failed, skipped, unknown or provisional tests cannot qualify")
    if summary.get("diagnostics") != []:
        raise ValueError("Missing or nonempty diagnostic evidence")
    if summary.get("crashed") is not False or summary.get("not_responding") is not False:
        raise ValueError("Missing or failing runtime health evidence")


if __name__ == "__main__":
    try:
        validate(json.loads(Path(sys.argv[1]).read_text()), sys.argv[2], sys.argv[3])
    except (ValueError, TypeError, KeyError) as exc:
        sys.exit(str(exc))
    print("Exact app and public-suite evidence accepted; signing approval remains separate")
