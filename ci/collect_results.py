#!/usr/bin/env python3
"""Turns the run's JUnit results into full-app.json, the file the summary (run_summary.py) and the
picture page (run_report.py) are built from. One checklist item per category and kind
("1 Turn-by-turn guidance: law", "... decisions", "... screen"), plus "App unit tests". A screen
test's screenshots (<test name>*.png in MEDIA_DIR) are attached to it for the picture page.

Usage: collect_results.py FULL_APP_JSON RUN_URL APP_COMMIT TESTS_SHA MEDIA_DIR JUNIT_XML_DIR...
"""
import json
import hashlib
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from test_selection import selected
from process_contract import validate_acceptance
from dmn_android_reader_evidence import verify as verify_android_dmn_reader

CATEGORIES = {"cat01": "1 Turn-by-turn guidance", "cat02": "2 Voice and sound", "cat03": "3 Lane guidance", "cat04": "4 Junction view", "cat05": "5 3D immersive navigation", "cat06": "6 Speed limits", "cat07": "7 Road alerts", "cat08": "8 Rerouting", "cat09": "9 Traffic", "cat10": "10 Position and GPS"}


def item_for(classname):
    m = re.match(r"com\.drivemate\.tests\.(cat\d+)\.\w+?(Law|Decisions|Screen)Test$", classname)
    if m:
        return f'{CATEGORIES.get(m.group(1), m.group(1))}: {m.group(2).lower()}'
    if classname.startswith("com.drivemate.app."):
        return "App unit tests"
    return None


def results(xml_dir, source_items=None):
    for f in sorted(Path(xml_dir).rglob("TEST-*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            classname = case.get("classname", "")
            item = (source_items or {}).get(classname) or item_for(classname)
            unmapped = item is None
            if unmapped:
                item = 'Unmapped test results'
            bad = case.find("failure") if case.find("failure") is not None else case.find("error")
            test = {"class": classname, "name": case.get("name"), "status": "failed" if bad is not None else "passed"}
            if case.find("skipped") is not None:
                test["status"] = "skipped"
            if bad is not None:
                test["failure"] = (bad.get("message") or bad.text or "failed").strip()
            if unmapped:
                test['status']='failed'; test['failure']='UNMAPPED_TEST_CLASS:'+classname
            yield item, test



def verify_real_offline_component_receipt(test_name, path, source_file):
    """Check actual Android receipt against pinned published test source.

    This is a component test receipt, NEVER whole-UK offline acceptance.
    Device-side real network probes and app file hashes remain supported by the
    Kotlin test's JUnit pass plus receipt values; this collector checks their
    scope and release identity and rejects missing/tampered/mislabelled files.
    """
    path=Path(path)
    source_file=Path(source_file)
    if (path.is_symlink() or not path.is_file() or path.stat().st_size>4096
        or source_file.is_symlink() or not source_file.is_file()
        or source_file.stat().st_size>2*1024*1024):
        raise ValueError("OFFLINE_COMPONENT_FILE_INVALID")
    try:
        record=json.loads(path.read_text())
        source=json.loads(source_file.read_text())
        published=source['regions']['stockport']['map']
        catalog_sha=source['test_source']['catalog_sha256']
    except (UnicodeError,ValueError,KeyError,TypeError):
        raise ValueError("OFFLINE_COMPONENT_RECORD_INVALID") from None
    if record.get("schema")!=1:
        raise ValueError("OFFLINE_COMPONENT_RECORD_INVALID")
    if test_name=="publishedStockportInstalledByProductionRegionDownloader":
        if (record.get("scope")!="published_stockport_display_install_only"
            or record.get("offline_route_accepted") is not False
            or record.get("screen_guidance_accepted") is not False):
            raise ValueError("OFFLINE_COMPONENT_SCOPE_INVALID")
        if (record.get("published_catalog_sha256")!=catalog_sha
            or record.get("region_id")!="stockport"
            or record.get("production_installer")!="RegionPacks.sync"
            or record.get("published_map_tag")!=published['tag']
            or record.get("installed_sha256")!=published['sha256']
            or type(record.get("installed_bytes")) is not int
            or record.get("installed_bytes")!=published['bytes']):
            raise ValueError("OFFLINE_COMPONENT_SOURCE_MISMATCH")
        return {"scope":"published_stockport_display_install_only",
                "region":"stockport","installed_sha256":published["sha256"],
                "published_catalog_sha256":catalog_sha,
                "offline_navigation_accepted":False}
    if test_name=="bundledManchesterRouterActuallyComputesWithoutNetwork":
        if (record.get("scope")!="bundled_dmn_greater_manchester_offline_route_only"
            or record.get("graph_source")!="bundled_app_asset_not_published_whole_uk"
            or record.get("published_whole_uk_graph_verified") is not False
            or record.get("production_guidance_screen_verified") is not False
            or record.get("legal_truth_verified") is not False
            or record.get("full_offline_acceptance") is not False):
            raise ValueError("OFFLINE_COMPONENT_SCOPE_INVALID")
        if (record.get("network_blocked_before_route") is not True
            or record.get("network_blocked_after_route") is not True):
            raise ValueError("OFFLINE_COMPONENT_NETWORK_UNVERIFIED")
        if (type(record.get("route_points")) is not int or record["route_points"]<2
            or type(record.get("route_steps")) is not int or record["route_steps"]<1):
            raise ValueError("OFFLINE_COMPONENT_ROUTE_UNVERIFIED")
        return {"scope":"bundled_dmn_greater_manchester_offline_route_only",
                "route_points":record["route_points"],
                "route_steps":record["route_steps"],
                "network_isolation_reported":True,
                "offline_navigation_accepted":False}
    raise ValueError("OFFLINE_COMPONENT_UNSUPPORTED_TEST")


def main():
    doc_path, run_url, app_commit, tests_sha, media_dir, *xml_dirs = sys.argv[1:]
    doc_path = Path(doc_path)
    shots = sorted(p.name for p in Path(media_dir).rglob("*.png")) if Path(media_dir).is_dir() else []
    # A result file from an earlier run/attempt must never contribute green
    # tests, receipts or app identity to this collection. Always rebuild from
    # the currently supplied JUnit inputs and immutable run arguments.
    doc = {"run_url": run_url, "app_commit": app_commit, "checklist": {}}
    doc["public_tests_sha"] = doc["drivemate_tests_sha"] = tests_sha
    # A compilation/emulator failure must not look green just because another suite ran.
    expected = []
    source_items = {}
    sources = list(Path("app-unit/java").rglob("*.kt")) + list(Path("categories").rglob("*.kt"))
    for source in sources:
        if not selected(source):
            continue
        text = source.read_text()
        package = re.search(r"^package\s+([\w.]+)", text, re.M)
        cls = re.search(r"class\s+(\w+)\b", text)
        if not package or not cls:
            continue
        classname = package.group(1) + "." + cls.group(1)
        if source.parts[0] == 'categories':
            category = re.match(r'(\d+)-', source.parts[1])
            kind = source.parts[2]
            if not category or kind not in ('law', 'decisions', 'screen'):
                raise ValueError('Test source has no recognized category/kind: ' + str(source))
            category_id = 'cat' + category.group(1)
            item = f'{CATEGORIES.get(category_id, category_id)}: {kind}'
        else:
            item = 'App unit tests'
        if item:
            if classname in source_items:
                raise ValueError('DUPLICATE_SOURCE_CLASS:'+classname)
            source_items[classname] = item
            expected += [(item, classname, name) for name in re.findall(r"@Test(?:\([^)]*\))?\s+fun\s+(\w+)", text)]
    found = [r for d in xml_dirs if Path(d).is_dir() for r in results(d, source_items)]
    seen_results=set()
    for _, test in found:
        identity=(test['class'],test['name'])
        if identity in seen_results:
            test['status']='failed'; test['failure']='DUPLICATE_TEST_RESULT'
        seen_results.add(identity)
    executed = {(t["class"], t["name"]) for _, t in found}
    for item, classname, name in expected:
        if (classname, name) not in executed:
            found.append((item, {"class": classname, "name": name, "status": "failed",
                "failure": "Expected test did not run; compilation or emulator setup may have failed"}))
    for _, test in found:
        test["media"] = [n for n in shots if n.startswith(test["name"])]
        if test['name'] == 'publishedGreatAncoatsSourceReachesSessionAndVisibleLaneStrip' and test['status']=='passed':
            evidence_files = list(Path(media_dir).rglob(test['name']+'-verdict.json'))
            try:
                if len(evidence_files)!=1 or evidence_files[0].stat().st_size>16384:
                    raise ValueError('MISSING_PIPELINE_EVIDENCE')
                raw=json.loads(evidence_files[0].read_text())
                evidence={k:raw[k] for k in ('case_id','country','mode','source_kind','claim','input_sha256','map_sha256','legal_truth','stages')}
                evidence.update(app_sha=app_commit,test_sha=tests_sha)
                fixture=Path('app-unit/resources/cat3-published-manchester-lanes-3.json').read_bytes()
                if hashlib.sha256(fixture).hexdigest()!=evidence['input_sha256']:
                    raise ValueError('PIPELINE_SOURCE_DRIFT')
                if evidence['map_sha256']!=json.loads(fixture)['source_sha256'] or evidence['case_id']!='osm-way-930354':
                    raise ValueError('PIPELINE_CASE_DRIFT')
                if evidence['stages']['source']['artifact_sha256']!=evidence['input_sha256']:
                    raise ValueError('PIPELINE_SOURCE_DRIFT')
                screenshots=list(Path(media_dir).rglob(test['name']+'.png'))
                if len(screenshots)!=1 or hashlib.sha256(screenshots[0].read_bytes()).hexdigest()!=evidence['stages']['screen']['artifact_sha256']:
                    raise ValueError('PIPELINE_SCREEN_EVIDENCE_MISMATCH')
                validate_acceptance(evidence)
                test['acceptance_evidence']=evidence
            except (OSError,ValueError,KeyError,TypeError) as exc:
                test['status']='failed'; test['failure']='Pipeline evidence rejected: '+str(exc)[:120]
    # Real Android component receipts are separate from published nationwide
    # offline acceptance. A JUnit green without its paired observed receipt is
    # a test failure, not an accepted navigation result.
    component_names={
        "publishedStockportInstalledByProductionRegionDownloader",
        "bundledManchesterRouterActuallyComputesWithoutNetwork",
    }
    for _, test in found:
        if test["name"] not in component_names or test["status"]!="passed":
            continue
        evidence=list(Path(media_dir).rglob(test["name"]+"-receipt.json"))
        try:
            if len(evidence)!=1:
                raise ValueError("OFFLINE_COMPONENT_RECEIPT_MISSING_OR_DUPLICATED")
            source=Path("app-src/app/src/androidTest/assets/stockport-live-region.json")
            verified=verify_real_offline_component_receipt(test["name"],evidence[0],source)
            test["offline_component_evidence"]=verified
            doc.setdefault("offline_component_evidence",[]).append({
                "case_id":test["name"],**verified})
        except (OSError,ValueError,KeyError,TypeError) as e:
            test["status"]="failed"
            test["failure"]="Offline component evidence rejected: "+str(e)[:120]
    # The real published road-pack reader needs its Android-written receipt, not
    # merely a green JUnit method. Missing or misleading evidence fails closed.
    for _, test in found:
        if test["name"]!="realPublishedStockportPieceLoadsThroughProductionAndroidDmnReader" or test["status"]!="passed":
            continue
        try:
            evidence=list(Path(media_dir).rglob("cat1-published-dmn-stockport-reader-receipt.json"))
            if len(evidence)!=1:
                raise ValueError("DMN_READER_RECEIPT_MISSING_OR_DUPLICATED")
            verified=verify_android_dmn_reader(
                evidence[0],
                "app-src/app/src/androidTest/assets/cat1-dmn-stockport-index.json",
                "app-src/app/src/androidTest/assets/cat1-dmn-stockport-piece.dmnpack")
            test["published_dmn_reader_evidence"]=verified
            doc.setdefault("published_dmn_reader_evidence",[]).append(verified)
        except (OSError,ValueError,KeyError,TypeError) as e:
            test["status"]="failed"
            test["failure"]="Published DMN reader evidence rejected: "+str(e)[:130]
    if not found:
        doc["checklist"]["drivemate-tests"] = {"tests": [{"class": "drivemate-tests", "name": "tests ran", "status": "failed",
                                                          "failure": "no results: the tests did not compile or run"}]}
    for item, test in found:
        doc["checklist"].setdefault(item, {"tests": []})["tests"].append(test)
    receipts=list(Path(media_dir).rglob('stockport-source-receipt.json'))
    if len(receipts)==1 and receipts[0].stat().st_size<=2048:
        receipt=json.loads(receipts[0].read_text())
        doc['source_receipts']=[{k:receipt[k] for k in ('attempts','generation_changed','request_superseded','index_loaded','scope')}]
    doc_path.parent.mkdir(parents=True, exist_ok=True)
    doc_path.write_text(json.dumps(doc, indent=1) + "\n")
    print(f"drivemate-tests: {sum(t['status'] == 'passed' for _, t in found)}/{len(found)} passed")


if __name__ == "__main__":
    main()

