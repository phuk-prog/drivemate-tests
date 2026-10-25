#!/usr/bin/env python3
"""Turns the run's JUnit results into full-app.json, the file the summary (run_summary.py) and the
picture page (run_report.py) are built from. One checklist item per category and kind
("1 Turn-by-turn guidance: law", "... decisions", "... screen"), plus "App unit tests". A screen
test's screenshots (<test name>*.png in MEDIA_DIR) are attached to it for the picture page.

Usage: collect_results.py FULL_APP_JSON RUN_URL APP_COMMIT TESTS_SHA MEDIA_DIR JUNIT_XML_DIR...
"""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

CATEGORIES = {"cat01": "1 Turn-by-turn guidance", "cat02": "2 Voice and sound"}


def item_for(classname):
    m = re.match(r"com\.drivemate\.tests\.(cat\d+)\.\w+?(Law|Decisions|Screen)Test$", classname)
    if m:
        return f'{CATEGORIES.get(m.group(1), m.group(1))}: {m.group(2).lower()}'
    if classname.startswith("com.drivemate.app."):
        return "App unit tests"
    return None


def results(xml_dir):
    for f in sorted(Path(xml_dir).rglob("TEST-*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            classname = case.get("classname", "")
            item = item_for(classname)
            if item is None:
                continue
            bad = case.find("failure") if case.find("failure") is not None else case.find("error")
            test = {"class": classname, "name": case.get("name"), "status": "failed" if bad is not None else "passed"}
            if case.find("skipped") is not None:
                test["status"] = "skipped"
            if bad is not None:
                test["failure"] = (bad.get("message") or bad.text or "failed").strip()
            yield item, test


def main():
    doc_path, run_url, app_commit, tests_sha, media_dir, *xml_dirs = sys.argv[1:]
    doc_path = Path(doc_path)
    shots = sorted(p.name for p in Path(media_dir).rglob("*.png")) if Path(media_dir).is_dir() else []
    doc = json.loads(doc_path.read_text()) if doc_path.is_file() else {
        "run_url": run_url, "app_commit": app_commit, "checklist": {}}
    doc["public_tests_sha"] = doc["drivemate_tests_sha"] = tests_sha
    found = [r for d in xml_dirs if Path(d).is_dir() for r in results(d)]
    for _, test in found:
        test["media"] = [n for n in shots if n.startswith(test["name"])]
    if not found:
        doc["checklist"]["drivemate-tests"] = {"tests": [{"class": "drivemate-tests", "name": "tests ran", "status": "failed",
                                                          "failure": "no results: the tests did not compile or run"}]}
    for item, test in found:
        doc["checklist"].setdefault(item, {"tests": []})["tests"].append(test)
    doc_path.parent.mkdir(parents=True, exist_ok=True)
    doc_path.write_text(json.dumps(doc, indent=1) + "\n")
    print(f"drivemate-tests: {sum(t['status'] == 'passed' for _, t in found)}/{len(found)} passed")


if __name__ == "__main__":
    main()
