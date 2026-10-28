"""Classify production app URL references within the existing 23 test categories.

Static source inventory, not a compiled APK/network test. No requests are made,
no secrets or full private source are printed, and links without an ownership
classification remain explicit review findings. A correct URL cannot establish
the existence of an asset or a working on-device download.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

URL = re.compile(r"https?://[^\s\"'<>\\]+", re.IGNORECASE)
GITHUB_RELEASE = re.compile(
    r"^https://github\.com/(?P<repo>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/releases/download/"
    r"(?P<tag>[A-Za-z0-9._-]+)", re.IGNORECASE
)
# Scoped to current *new* category numbering, not historical folder IDs.
# A path not on this list is still found, but flagged unclassified.
SOURCE_CATEGORIES = {
    "app/build.gradle.kts": ([1, 4], "runtime_build_config"),
    "data/RoadPack.kt": ([1, 4], "network_download"),
    "data/RegionPacks.kt": ([1, 4], "network_download"),
    "data/MapData.kt": ([1, 5], "network_download"),
    "data/OwnMap.kt": ([5], "attribution_or_reference"),
    "data/TomTom.kt": ([16], "network_service"),
    "data/NationalHighways.kt": ([16], "network_service"),
    "data/UkRoadFeeds.kt": ([16], "network_service"),
    "data/RouteWeather.kt": ([15], "network_service"),
    "data/FuelPrices.kt": ([17], "network_service"),
    "data/ReviewImagery.kt": ([22], "network_service"),
    "osm/OsmConnection.kt": ([22], "network_service"),
    "account/DriveAppDataBackup.kt": ([3], "network_service"),
    "controller/NavigationSession.kt": ([22], "network_service"),
    "controller/ShareText.kt": ([19], "attribution_or_reference"),
    "data/DataCredits.kt": ([20], "attribution_or_reference"),
    "ui/MainMenu.kt": ([20], "attribution_or_reference"),
    "PrivacyNotice.kt": ([20], "attribution_or_reference"),
}
OLD_PROJECTS = frozenset({"phuk-prog/drivemate-data", "phuk-prog/DriveMate-Navigation",
                          "phuk-prog/DriveMate"})


def source_role(path):
    if path == "app/build.gradle.kts":
        return SOURCE_CATEGORIES[path]
    prefix = "app/src/main/java/com/drivemate/app/"
    if path.startswith(prefix):
        key = path[len(prefix):]
        return SOURCE_CATEGORIES.get(key, ([], "unclassified_runtime_source"))
    return ([], "out_of_scope")


def classify_url(value):
    # A closing bracket is part of a valid bare IPv6 authority.
    raw = value.rstrip(".,;)}")
    try:
        parsed = urlsplit(raw)
        host = (parsed.hostname or "").lower()
        parsed.port  # Validate malformed/out-of-range ports too.
    except ValueError:
        # Do not publish the raw token or parser exception: it may contain keys.
        return {"status": "invalid_url", "host": "", "repository": None}
    if not host or parsed.scheme not in {"https", "http"}:
        return {"status": "invalid_url", "host": host, "repository": None}
    found = GITHUB_RELEASE.match(raw)
    repo = found.group("repo") if found else None
    if repo and repo.casefold() in {x.casefold() for x in OLD_PROJECTS}:
        status = "old_or_unreachable_project_target"
    elif parsed.scheme != "https":
        status = "insecure_http_reference"
    elif host == "master.apis.dev.openstreetmap.org":
        status = "test_service_reference_not_live"
    else:
        status = "static_reference_only"
    return {"status": status, "host": host, "repository": repo,
            "release_tag": found.group("tag") if found else None}


def classify_source_text(path, content):
    categories, role = source_role(path)
    records = []
    for number, line in enumerate(content.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith(("//", "*", "/*", "#")):
            continue
        for match in URL.finditer(line):
            inspected = classify_url(match.group(0))
            inspected.update({
                "source_path": path, "line": number,
                "categories": categories if categories else [2],
                "source_role": role, "category_assignment": "assigned" if categories else "needs_review",
                # Hash only; full URLs may include dynamic query strings or keys.
                "reference_sha256": hashlib.sha256(match.group(0).encode()).hexdigest(),
            })
            records.append(inspected)
    return records


def audit(app_root, programme, alignment=None, batch="all"):
    app_root = Path(app_root)
    if programme.get("schema") != 1 or set(programme["execution_order"]) != set(range(1, 24)):
        raise ValueError("CATEGORY_PROGRAMME_INVALID")
    batches = {x["id"]: set(x["categories"]) for x in programme["batches"]}
    if batch != "all" and batch not in batches:
        raise ValueError("UNKNOWN_TEST_BATCH")
    chosen = set(range(1, 24)) if batch == "all" else batches[batch]
    source = app_root / "app/src/main/java"
    if not source.is_dir():
        raise ValueError("PRODUCTION_APP_SOURCES_MISSING")
    files = sorted(list(source.rglob("*.kt")) + list(source.rglob("*.java")))
    gradle = app_root / "app/build.gradle.kts"
    if gradle.is_file():
        files.append(gradle)
    if not files:
        raise ValueError("NO_PRODUCTION_SOURCE_FILES")
    records = []
    for p in files:
        if p.is_symlink() or not p.is_file() or p.stat().st_size > 1024 * 1024:
            raise ValueError("UNSAFE_OR_OVERSIZED_SOURCE_FILE")
        relative = p.relative_to(app_root).as_posix()
        records.extend(classify_source_text(relative, p.read_text(encoding="utf-8")))
        if len(records) > 5000:
            raise ValueError("TOO_MANY_SOURCE_REFERENCES")
    alignment = alignment or {}
    if alignment and (
        alignment.get("scope") != "checked_out_source_routing_publisher_alignment_only"
        or alignment.get("offline_acceptance") is not False
        or alignment.get("reason") not in {
            "routing_release_repository_mismatch", "source_repositories_aligned_only",
            "app_target_unverified", "publisher_target_unverified"
        }
    ):
        raise ValueError("INVALID_ROUTING_ALIGNMENT_RECEIPT")
    mapping = {}
    for category in programme["categories"]:
        cid = category["id"]
        subset = [x for x in records if cid in x["categories"]] if cid in chosen else []
        flags = sorted(set(
            x["status"] for x in subset if x["status"] in {
                "old_or_unreachable_project_target", "invalid_url", "insecure_http_reference"
            }
        ))
        if any(x["category_assignment"] == "needs_review" for x in subset):
            flags.append("unassigned_source_needs_review")
        if cid in (1, 4) and cid in chosen and alignment:
            if alignment["reason"] == "routing_release_repository_mismatch":
                flags.append("routing_release_repository_mismatch")
            elif alignment["reason"] != "source_repositories_aligned_only":
                flags.append("routing_publisher_unverified")
        if cid not in chosen:
            status = "not_selected"
        elif flags:
            status = "blocked_needs_investigation"
        elif not subset:
            status = "no_static_url_observed"
        else:
            status = "static_inventory_only"
        mapping[str(cid)] = {
            "name": category["name"], "status": status,
            "references": len(subset), "finding_codes": sorted(set(flags)),
            "live_http_verified": False, "installed_apk_verified": False,
            "download_runtime_verified": False,
        }
    return {
        "schema": 1, "scope": "production_source_static_connection_inventory",
        "category_numbering": "current_1_to_23", "batch": batch,
        "source_files_scanned": len(files), "url_references": len(records),
        "categories": mapping, "references": [
            r for r in records if any(c in chosen for c in r["categories"])
        ],
        "runtime_network_verified": False, "apk_binary_inspected": False,
        "limitations": [
            "Static Kotlin/Java and Gradle URL literals only: no APK binary, runtime-composed URLs, redirects, HTTP handshakes, permissions, or installed app request trace",
            "Static URL/reference matches are not evidence of an operational service, downloaded assets, or route-to-screen navigation",
            "Unknown ownership remains a review finding in Reliability category 2; unused and attribution URLs are not assumed live network requests",
        ],
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--app-root", required=True)
    p.add_argument("--programme", default="ci/programme.json")
    p.add_argument("--routing-alignment")
    p.add_argument("--batch", default="all")
    p.add_argument("--output", required=True)
    args = p.parse_args()
    receipt = None
    if args.routing_alignment:
        file = Path(args.routing_alignment)
        if file.is_symlink() or not file.is_file() or file.stat().st_size > 8192:
            raise ValueError("INVALID_ROUTING_ALIGNMENT_FILE")
        receipt = json.loads(file.read_text())
    report = audit(args.app_root, json.loads(Path(args.programme).read_text()),
                   alignment=receipt, batch=args.batch)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "source_files": report["source_files_scanned"],
        "url_references": report["url_references"],
        "blocked_categories": [k for k, v in report["categories"].items()
                               if v["status"] == "blocked_needs_investigation"],
        "apk_binary_inspected": False,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
