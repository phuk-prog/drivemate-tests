#!/usr/bin/env python3
"""Step 1 of the owner-approved signed APK pipeline: verify exact-source test success.

All tests run in the public repository drivemate-tests (REGISTER.md). Its "Test the app" run
publishes an artifact named drivemate-tests-<app commit>, and the run only succeeds when every
test passed. This looks for that artifact and its run's result; it downloads nothing.

drivemate-tests starts a run by itself for each new main commit (watch-app.yml), so this waits for
the result for up to WAIT_MINUTES.

Usage: check_drivemate_tests.py APP_COMMIT
"""
import json
import os
import re
import sys
import time
from datetime import datetime
from urllib.request import Request, urlopen

REPO = "phuk-prog/drivemate-tests"
WAIT_MINUTES = 60


def get(path):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "DriveMate-Release-Pipeline"}
    if os.environ.get("GH_TOKEN"):  # Only for rate limits; the repository is public.
        headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
    with urlopen(Request(f"https://api.github.com/repos/{REPO}/{path}", headers=headers), timeout=30) as r:
        return json.load(r)


def newest_run(commit):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("An exact app commit SHA is required")
    suite = get("git/ref/heads/main")["object"]["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", suite):
        raise ValueError("Cannot resolve the current public test suite")
    # This job only checks source-matched automated tests for a private,
    # signed APK. Independent final/road acceptance and any public
    # distribution decision belong to their separate review process.
    # Never treat this check as safety approval.
    runs = {}
    eligible = {}
    candidates = {}
    page = 1
    while True:
        artifacts = get(f"actions/artifacts?name=drivemate-tests-{commit}&per_page=100&page={page}")["artifacts"]
        for artifact in artifacts:
            run_id = artifact["workflow_run"]["id"]
            if run_id not in runs:
                runs[run_id] = get(f"actions/runs/{run_id}")
            run = runs[run_id]
            if (run.get("head_sha") != suite or run.get("head_branch") != "main"
                    or run.get("path") != ".github/workflows/test-app.yml"
                    or run.get("event") not in ("push", "workflow_dispatch")):
                continue
            candidates[run_id] = run
            if artifact.get("expired") is not False:
                continue
            # A rerun can leave an artifact from its earlier attempt. Only an artifact
            # refreshed after this attempt started is evidence for that attempt.
            if run.get("run_attempt", 0) != 1:
                try:
                    updated = datetime.fromisoformat(artifact["updated_at"].replace("Z", "+00:00"))
                    started = datetime.fromisoformat(run["run_started_at"].replace("Z", "+00:00"))
                    if updated.tzinfo is None or started.tzinfo is None or updated < started:
                        continue
                except (KeyError, TypeError, ValueError):
                    continue
            # Store eligible runs separately: a newer failure must beat an older success.
            eligible[run_id] = run
        if len(artifacts) < 100:
            break
        page += 1
    if not candidates:
        return None
    newest = max(candidates.values(), key=lambda r: (r.get("run_started_at", r["created_at"]), r["id"]))
    # Missing evidence from the latest matching run cannot resurrect an older success.
    return eligible.get(newest["id"])


def main(commit):
    deadline = time.time() + WAIT_MINUTES * 60
    while True:
        run = newest_run(commit)
        if run and run["status"] == "completed":
            print(f"Newest drivemate-tests run for {commit[:7]}: {run['conclusion']} ({run['html_url']})")
            return 0 if run["conclusion"] == "success" else 1
        if time.time() > deadline:
            print(f"No finished drivemate-tests run for app commit {commit} after {WAIT_MINUTES} minutes. "
                  f"Run \"Test the app\" in {REPO} for it, then re-run this workflow.")
            return 1
        time.sleep(60)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))

