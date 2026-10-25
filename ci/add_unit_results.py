#!/usr/bin/env python3
"""Adds drivemate-tests' fast tests (JUnit XML) to a run's full-app.json, so they appear in the
same summary.json and picture page as the emulator tests. One checklist item per category and kind:
"1 Turn-by-turn guidance: law" and "1 Turn-by-turn guidance: decisions".

Usage: add_unit_results.py JUNIT_XML_DIR FULL_APP_JSON RUN_URL APP_COMMIT TESTS_SHA
"""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

CATEGORIES = {"cat01": "1 Turn-by-turn guidance"}


def item_for(classname):
    m = re.match(r"com\.drivemate\.tests\.(cat\d+)\.\w+?(Law|Decisions)Test$", classname)
    if not m:
        return None
    return f'{CATEGORIES.get(m.group(1), m.group(1))}: {"law" if m.group(2) == "Law" else "decisions"}'


def results(xml_dir):
    for f in sorted(Path(xml_dir).glob("TEST-com.drivemate.tests.*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            item = item_for(case.get("classname", ""))
            if item is None:
                continue
            bad = case.find("failure") if case.find("failure") is not None else case.find("error")
            test = {"name": case.get("name"), "status": "failed" if bad is not None else "passed"}
            if case.find("skipped") is not None:
                test["status"] = "skipped"
            if bad is not None:
                test["failure"] = (bad.get("message") or bad.text or "failed").strip()
            yield item, test


def main():
    xml_dir, doc_path, run_url, app_commit, tests_sha = sys.argv[1:6]
    doc_path = Path(doc_path)
    doc = json.loads(doc_path.read_text()) if doc_path.is_file() else {
        "run_url": run_url, "app_commit": app_commit, "public_tests_sha": "", "checklist": {}}
    doc["drivemate_tests_sha"] = tests_sha
    found = list(results(xml_dir))
    if not found:
        doc["checklist"]["drivemate-tests"] = {"tests": [{"name": "fast tests ran", "status": "failed",
                                                          "failure": "no results: the tests did not compile or run"}]}
    for item, test in found:
        doc["checklist"].setdefault(item, {"tests": []})["tests"].append(test)
    doc_path.parent.mkdir(parents=True, exist_ok=True)
    doc_path.write_text(json.dumps(doc, indent=1) + "\n")
    print(f"drivemate-tests: {sum(t['status'] == 'passed' for _, t in found)}/{len(found)} passed")


if __name__ == "__main__":
    main()
