#!/usr/bin/env python3
"""Source-only Category 1 DMN download-path diagnostic.

The report NEVER claims a real Android install, compatible UK graph, offline
route or driving acceptance. It checks an exact checked-out app revision and
is safe to publish because it contains only fixed source paths and SHA-256s.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

PATHS = {
    "map_ui": "app/src/main/java/com/drivemate/app/ui/MainMenu.kt",
    "map_worker": "app/src/main/java/com/drivemate/app/data/MapPack.kt",
    "whole_uk": "app/src/main/java/com/drivemate/app/data/WholeUk.kt",
    "catalogue": "app/src/main/java/com/drivemate/app/data/MapData.kt",
    "dmn": "app/src/main/java/com/drivemate/app/dmn/DmnTiles.kt",
    "legacy_roads": "app/src/main/java/com/drivemate/app/data/RoadPack.kt",
}
RULES = {
    "map_ui_requests_map_pack": ("map_ui", r'Choice\s*\(\s*"Download map"\s*,[\s\S]{0,150}?MapPack\.setWanted\s*\('),
    "secondary_ui_requests_road_pack": ("map_ui", r'Choice\s*\(\s*"Download UK roads"\s*,[\s\S]{0,150}?RoadPack\.setWanted\s*\('),
    "map_worker_calls_whole_uk": ("map_worker", r'WholeUk\.fetchAll\s*\(\s*ctx\s*\)'),
    "regional_snapshot_flag": ("catalogue", r'fun\s+isRegionalSnapshot\s*\(\s*\)\s*=\s*activeRegion\s*!=\s*null'),
    "regional_snapshot_skips_dmn": ("whole_uk", r'if\s*\(\s*!MapData\.isRegionalSnapshot\(\)\s*\)\s*DmnTiles\.fetchAll\s*\('),
    "skipped_branch_reports_complete": ("whole_uk", r'else\s+onProgress\s*\(\s*1f\s*\)'),
    "dmn_has_independent_uk_installer": ("dmn", r'fun\s+fetchAll\s*\(\s*dir\s*:\s*File'),
    "secondary_roads_use_separate_store": ("legacy_roads", r'object\s+RoadPack\s*\{'),
}
SHA_RE = re.compile(r"[0-9a-f]{40}\Z")


def assess_source(sources, app_sha):
    if not isinstance(app_sha, str) or not SHA_RE.fullmatch(app_sha):
        raise ValueError("APP_REVISION_NOT_PINNED")
    if not isinstance(sources, dict) or set(sources) != set(PATHS):
        raise ValueError("APP_SOURCE_COHORT_INCOMPLETE")
    for name, content in sources.items():
        if not isinstance(content, str) or not content:
            raise ValueError("INVALID_APP_SOURCE:" + name)
    checks = {key: re.search(pattern, sources[name]) is not None
              for key, (name, pattern) in RULES.items()}
    all_links = all(checks.values())
    return {
        "schema": 1,
        "issue": "CAT1-DMN-DOWNLOAD-PATH-01",
        "scope": "static_exact_app_source_only",
        "app_commit": app_sha,
        "checks": checks,
        "source_chain_verified": all_links,
        "finding": ("REGIONAL_SNAPSHOT_BYPASSES_DMN_FETCH_AND_COMPLETES_PROGRESS"
                    if all_links else "SOURCE_CONTRACT_CHANGED_REVIEW_REQUIRED"),
        "regional_snapshot_dmn_install_observed": False,
        "android_network_install_verified": False,
        "dmn_graph_compatibility_verified": False,
        "offline_route_to_screen_voice_verified": False,
        "real_device_behavior": "unknown",
        "release_allowed": False,
        "interpretation": ("A source branch can skip the DMN fetch while reporting 100%; "
                           "no phone-level reproduction, licensing or compatibility proven."),
    }


def inspect_files(app_root, app_sha):
    root = Path(app_root)
    if root.is_symlink() or not root.is_dir():
        raise ValueError("APP_CHECKOUT_UNAVAILABLE")
    sources, digests = {}, {}
    for key, relative in PATHS.items():
        file = root / relative
        if file.is_symlink() or not file.is_file() or file.stat().st_size > 2 * 1024 * 1024:
            raise ValueError("APP_SOURCE_FILE_INVALID:" + key)
        data = file.read_bytes()
        try:
            sources[key] = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ValueError("APP_SOURCE_UTF8_INVALID:" + key) from exc
        digests[key] = {"path": relative, "sha256": hashlib.sha256(data).hexdigest()}
    result = assess_source(sources, app_sha)
    result["source_files"] = digests
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-root", required=True)
    parser.add_argument("--app-sha", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    result = inspect_files(args.app_root, args.app_sha)
    dest = Path(args.output)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print("CAT1 DMN source check:", result["finding"], "- offline acceptance NOT verified")


if __name__ == "__main__":
    main()
