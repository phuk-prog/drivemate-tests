#!/usr/bin/env python3
"""Fail-open documentation-only detector for public DriveMate CI.

Only a proven Markdown/agent-instruction change skips heavy Android tests.
Manual runs, API errors, huge diffs and changes to tests/workflows always test.
Skipped CI has no release-authorising artifact.
"""
import json
import os
import sys
import urllib.parse
import urllib.request


def documentation_only(paths):
    """Do not classify source, fixtures, configs or unknown paths as docs."""
    paths = list(paths)
    return bool(paths) and all(
        p.endswith((".md", ".markdown")) or p in ("LICENSE", "NOTICE")
        for p in paths
    )


def load_json(path):
    req = urllib.request.Request(
        "https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/" + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + os.environ["GITHUB_TOKEN"],
            "User-Agent": "DriveMate-ci-scope",
        },
    )
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.load(response)


def changed_files(event):
    kind = os.environ.get("GITHUB_EVENT_NAME")
    if kind == "workflow_dispatch":
        return None
    if kind == "pull_request":
        number = event["pull_request"]["number"]
        names = []
        for page in range(1, 5):
            data = load_json(f"pulls/{number}/files?per_page=100&page={page}")
            names.extend(
                n for item in data
                for n in (item["filename"], item.get("previous_filename"))
                if n
            )
            if len(data) < 100:
                return names
        return None  # Too many files; fail open to full testing.
    if kind == "push":
        before, after = event.get("before"), event.get("after")
        if not before or before == "0" * 40 or not after:
            return None
        base = urllib.parse.quote(before, safe="")
        head = urllib.parse.quote(after, safe="")
        data = load_json(f"compare/{base}...{head}")
        files = data.get("files", [])
        if len(files) >= 300:
            return None
        return [
            n for item in files
            for n in (item["filename"], item.get("previous_filename"))
            if n
        ]
    return None


def main():
    full, reason = True, "Full testing required"
    try:
        with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as handle:
            changed = changed_files(json.load(handle))
        if changed is not None and documentation_only(changed):
            full, reason = False, "Documentation-only change; no APK or full-suite evidence"
        else:
            reason = "Manual, code/config change or unverified file scope: full suite"
    except Exception as exc:
        # Failing open is essential to avoid incorrectly skipping required tests.
        reason = f"Could not verify changed-file scope ({type(exc).__name__}); full suite"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"run_full={'true' if full else 'false'}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        summary.write(f"Run scope: **{reason}**\n")
    print(reason)
    return 0


if __name__ == "__main__":
    sys.exit(main())
