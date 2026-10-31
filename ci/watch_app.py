#!/usr/bin/env python3
"""Single public test-dispatch policy for a private DriveMate app commit.

Never equate an artifact's existence with a pass. Avoid duplicate jobs for
any active Test the app run and hold a known failed exact-input attempt for
manual retry; existing verified evidence must match current public tests SHA.
"""
import datetime
import json
import os
import re
import sys
import urllib.request

REPO = os.environ.get("GITHUB_REPOSITORY", "phuk-prog/drivemate-tests")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def get(path, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/{path}",
        data=body,
        method=method,
        headers={
            "Authorization": "Bearer " + os.environ["GH_TOKEN"],
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "DriveMate-public-test-watcher",
        },
    )
    with urllib.request.urlopen(req, timeout=35) as response:
        return json.load(response) if method == "GET" else None


def target_name(app_sha):
    return f"DriveMate app {app_sha} suite "


def matches_target(run, app_sha, suite_sha):
    return (
        run.get("head_sha") == suite_sha
        and run.get("event") == "workflow_dispatch"
        and run.get("display_title", "").startswith(target_name(app_sha))
        and f" suite {suite_sha} batch all" in run.get("display_title", "")
    )


def valid_artifact(artifact, run, suite_sha):
    if (
        artifact.get("expired") is not False
        or run.get("status") != "completed"
        or run.get("conclusion") != "success"
        or run.get("head_sha") != suite_sha
        or run.get("head_branch") != "main"
        or run.get("path") != ".github/workflows/test-app.yml"
        or run.get("event") not in ("push", "workflow_dispatch")
    ):
        return False
    if run.get("run_attempt", 1) > 1:
        try:
            updated = datetime.datetime.fromisoformat(artifact["updated_at"].replace("Z", "+00:00"))
            started = datetime.datetime.fromisoformat(run["run_started_at"].replace("Z", "+00:00"))
            if updated < started:
                return False
        except (TypeError, KeyError, ValueError):
            return False
    return True


def decide(runs, receipts, app_sha, suite_sha):
    """Receipts are (artifact, matching run) tuples, without sensitive data."""
    # A global in-progress full test can consume the emulator and limited CI.
    if any(run.get("status") != "completed" for run in runs):
        return "busy"
    matching = [r for r in runs if matches_target(r, app_sha, suite_sha)]
    matching.sort(key=lambda r: (r.get("run_started_at") or r.get("created_at") or "", r.get("id", 0)), reverse=True)
    last = matching[0] if matching else None
    if last and last.get("conclusion") != "success":
        return "failed_hold"  # Manual retry, not an automatic 30-minute loop.
    evidence = [
        run for artifact, run in receipts
        if valid_artifact(artifact, run, suite_sha)
    ]
    if last and not any(r.get("id") == last.get("id") for r in evidence):
        return "dispatch"  # A successful but artifact-less run cannot pass.
    return "verified" if evidence else "dispatch"


def main():
    app_sha, suite_sha = os.environ["APP_SHA"], os.environ["GITHUB_SHA"]
    if not SHA_RE.fullmatch(app_sha) or not SHA_RE.fullmatch(suite_sha):
        raise ValueError("Exact app/test commit SHA required")
    recent = get("actions/workflows/test-app.yml/runs?per_page=100")["workflow_runs"]
    # If this page is saturated, avoid dispatch without visibility of in-flight work.
    if len(recent) == 100:
        print("100 recent runs reached: defer rather than risk duplicate execution")
        return 0
    artifacts = get(f"actions/artifacts?name=drivemate-tests-{app_sha}&per_page=100")["artifacts"]
    if len(artifacts) == 100:
        print("100 matching artifacts reached: defer to avoid unsafe status inference")
        return 0
    receipt_runs = {r["id"]: r for r in recent}
    receipts = []
    for artifact in artifacts:
        run_id = artifact.get("workflow_run", {}).get("id")
        if run_id and run_id not in receipt_runs:
            receipt_runs[run_id] = get(f"actions/runs/{run_id}")
        if run_id in receipt_runs:
            receipts.append((artifact, receipt_runs[run_id]))
    decision = decide(recent, receipts, app_sha, suite_sha)
    messages = {
        "verified": f"Full suite already verified for app {app_sha[:10]} and tests {suite_sha[:10]}.",
        "busy": "A public test run is queued/running; no duplicate test dispatch.",
        "failed_hold": f"Latest exact-input test failed for app {app_sha[:10]}; manual retry required, no repeated automatic failures.",
    }
    if decision == "dispatch":
        get("actions/workflows/test-app.yml/dispatches", "POST", {
            "ref": "main", "inputs": {"app_ref": app_sha, "batch": "all"}
        })
        print(f"Started one public full suite: app {app_sha[:10]}, tests {suite_sha[:10]}.")
    else:
        print(messages[decision])
    return 0


if __name__ == "__main__":
    sys.exit(main())
