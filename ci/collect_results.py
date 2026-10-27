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

CATEGORIES = {"cat01": "1 Turn-by-turn guidance", "cat02": "2 Voice and sound", "cat03": "3 Lane guidance", "cat04": "4 Junction view", "cat05": "5 3D immersive navigation", "cat06": "6 Speed limits", "cat07": "7 Road alerts", "cat08": "8 Rerouting", "cat09": "9 Traffic", "cat10": "10 Position and GPS"}
INTEGRITY = "Test collection integrity"


def collection_failure(classname, name, message):
    return INTEGRITY, {"class": classname, "name": name, "status": "failed", "failure": message}


def item_for(classname, source_kind=None):
    if source_kind:
        m = re.fullmatch(r"com\.drivemate\.tests\.(cat\d+)\.\w+Test", classname)
        if m and m.group(1) in CATEGORIES:
            return f'{CATEGORIES[m.group(1)]}: {source_kind}'
    m = re.match(r"com\.drivemate\.tests\.(cat\d+)\.\w+?(Law|Decisions|Screen)Test$", classname)
    if m:
        return f'{CATEGORIES.get(m.group(1), m.group(1))}: {m.group(2).lower()}'
    if classname.startswith("com.drivemate.app."):
        return "App unit tests"
    return None


def results(xml_dir, source_items):
    for f in sorted(Path(xml_dir).rglob("TEST-*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            classname = case.get("classname", "")
            item = source_items.get(classname) or item_for(classname)
            if item is None:
                yield collection_failure(classname, case.get("name") or "unnamed result",
                    "Unrecognised JUnit class; test result cannot be excluded from the gate")
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
    # Always rebuild: a second collection must not retain stale passing results.
    doc = {"run_url": run_url, "app_commit": app_commit, "checklist": {}}
    doc["public_tests_sha"] = doc["drivemate_tests_sha"] = tests_sha
    found = []
    source_items = {}
    # A compilation/emulator failure must not look green just because another suite ran.
    expected = []
    source_ids = set()
    sources = list(Path("app-unit/java").rglob("*.kt")) + list(Path("categories").rglob("*.kt"))
    for source in sources:
        text = source.read_text()
        annotations = list(re.finditer(r"@(?:org\.junit\.)?Test\b", text))
        if not annotations:
            continue
        package = re.search(r"^package\s+([\w.]+)", text, re.M)
        classes = re.findall(r"\bclass\s+(\w+Test)\b", text)
        if not package or len(classes) != 1:
            found.append(collection_failure(str(source), "source discovery",
                "Unrecognised test source layout; explicit discovery support is required"))
            continue
        cls = classes[0]
        classname = package.group(1) + "." + cls
        kind = source.parent.name if source.parent.name in ("law", "decisions", "screen") else None
        item = item_for(classname, kind)
        if item is None:
            found.append(collection_failure(classname, "source discovery",
                "Unrecognised test source class; cannot silently exclude annotated methods"))
            continue
        source_items[classname] = item
        for index, annotation in enumerate(annotations):
            end = annotations[index + 1].start() if index + 1 < len(annotations) else len(text)
            method = re.search(r"\bfun\s+(?:`([^`]+)`|(\w+))\s*\(", text[annotation.end():end])
            if not method:
                found.append(collection_failure(classname, f"source annotation {index + 1}",
                    "Test source annotation has no discoverable method"))
                continue
            name = method.group(1) or method.group(2)
            identity = (classname, name)
            if identity in source_ids:
                found.append(collection_failure(classname, name, "Duplicate test source identity"))
            source_ids.add(identity)
            expected.append((item, classname, name))
    found += [r for d in xml_dirs if Path(d).is_dir() for r in results(d, source_items)]
    result_ids = set()
    for _, test in list(found):
        if test["class"] and test["name"]:
            identity = (test["class"], test["name"])
            if identity in result_ids:
                found.append(collection_failure(*identity, "Duplicate JUnit test identity"))
            result_ids.add(identity)
        else:
            found.append(collection_failure(test["class"], "unnamed result", "JUnit identity is missing"))
    executed = {(t["class"], t["name"]) for _, t in found}
    for item, classname, name in expected:
        if (classname, name) not in executed:
            found.append((item, {"class": classname, "name": name, "status": "failed",
                "failure": "Expected test did not run; compilation or emulator setup may have failed"}))
    for _, test in found:
        test["media"] = [n for n in shots if test["name"] and n.startswith(test["name"])]
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
