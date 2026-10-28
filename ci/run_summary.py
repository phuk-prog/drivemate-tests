#!/usr/bin/env python3
"""One short summary per emulator run: every test, passed or failed, and why.
Read this instead of the long logs. Built from the run's own full-app.json; a frozen screen or crash
is flagged from the files the run saved beside it.

Usage: run_summary.py FULL_APP_JSON OUT_JSON
"""
import json
from pathlib import Path
import sys
from process_contract import programme_summary

WHY_CHARS = 300


def build(doc, results_dir):
    results_dir = Path(results_dir)
    tests = [{"item": item, "class": t.get("class", ""), "name": t["name"], "status": t["status"],
              **({"why": str(t["failure"])[:WHY_CHARS]} if t.get("failure") else {})}
             for item, group in doc["checklist"].items() for t in group["tests"]]
    anr = results_dir / "anr-traces.txt"
    crash = results_dir / "crash-log.txt"
    return {
        "run_url": doc["run_url"],
        "run_attempt": doc.get("run_attempt"),
        "app_commit": doc["app_commit"],
        "public_tests_sha": doc["public_tests_sha"],
        "drivemate_tests_sha": doc.get("drivemate_tests_sha", ""),
        "passed": sum(t["status"] == "passed" for t in tests),
        "total": len(tests),
        "failed": sum(t['status'] == 'failed' for t in tests),
        "unknown": sum(t['status'] == 'unknown' for t in tests),
        "provisional": sum(t['status'] == 'provisional' for t in tests),
        "skipped": sum(t['status'] == 'skipped' for t in tests),
        "programme": programme_summary(doc),
        "acceptance_evidence": [t['acceptance_evidence'] for g in doc['checklist'].values()
                                for t in g['tests'] if 'acceptance_evidence' in t],
        "source_receipts": doc.get('source_receipts',[]),
        "not_responding": anr.is_file() and "== /data/anr/" in anr.read_text(errors="replace"),
        "crashed": crash.is_file() and "FATAL EXCEPTION" in crash.read_text(errors="replace"),
        "tests": sorted(tests, key=lambda t: (t["status"] == "passed", t["item"], t["name"])),
    }


def main():
    source = Path(sys.argv[1])
    summary = build(json.loads(source.read_text()), source.parent)
    Path(sys.argv[2]).write_text(json.dumps(summary, indent=1) + "\n")


if __name__ == "__main__":
    main()
