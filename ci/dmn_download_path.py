#!/usr/bin/env python3
"""Source-only DMN UK road download and GPS matching contract.

This checks the exact app source. It is not a claim about installed graph bytes,
road-law truth, network-isolated journeys or independent final acceptance.
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
    "dmn": "app/src/main/java/com/drivemate/app/dmn/DmnTiles.kt",
    "router": "app/src/main/java/com/drivemate/app/data/Router.kt",
    "session": "app/src/main/java/com/drivemate/app/controller/NavigationSession.kt",
}
RULES = {
    "map_ui_requests_map_pack": ("map_ui", r'Choice\s*\(\s*"Download map"\s*,[\s\S]{0,150}?MapPack\.setWanted\s*\('),
    "map_worker_calls_whole_uk": ("map_worker", r'WholeUk\.fetchAll\s*\(\s*ctx\s*\)'),
    "whole_uk_fetches_dmn": ("whole_uk", r'DmnTiles\.fetchAll\s*\(\s*dmn\s*\)'),
    "dmn_has_uk_installer": ("dmn", r'fun\s+fetchAll\s*\(\s*dir\s*:\s*File'),
    "router_selects_dmn": ("router", r'com\.drivemate\.app\.dmn\.Dmn\.route\s*\('),
    "gps_road_evidence_uses_dmn": ("session", r'com\.drivemate\.app\.dmn\.Dmn\.matchTrace\s*\('),
}
SHA_RE = re.compile(r"[0-9a-f]{40}\Z")


def assess_source(sources, app_sha):
    if not isinstance(app_sha, str) or not SHA_RE.fullmatch(app_sha):
        raise ValueError("APP_REVISION_NOT_PINNED")
    if not isinstance(sources, dict) or set(sources) != set(PATHS):
        raise ValueError("APP_SOURCE_COHORT_INCOMPLETE")
    if any(not isinstance(value, str) or not value for value in sources.values()):
        raise ValueError("INVALID_APP_SOURCE")
    checks = {key: re.search(pattern, sources[name]) is not None
              for key, (name, pattern) in RULES.items()}
    checks["regional_imagery_cannot_skip_dmn"] = (
        re.search(r'if\s*\(\s*!MapData\.isRegionalSnapshot\(\)\s*\)\s*DmnTiles\.fetchAll', sources["whole_uk"]) is None
        and re.search(r'^\s*DmnTiles\.fetchAll\s*\(\s*dmn\s*\)', sources["whole_uk"], re.M) is not None
    )
    checks["no_secondary_legacy_routing_toggle"] = (
        "RoadPack." not in sources["map_ui"]
        and '"Download UK roads"' not in sources["map_ui"]
    )
    aligned = all(checks.values())
    return {
        "schema": 1,
        "issue": "M02-DMN-SOURCE-PATH",
        "scope": "static_exact_app_source_only",
        "app_commit": app_sha,
        "checks": checks,
        "source_chain_verified": aligned,
        "finding": "DMN_SOURCE_PATH_ALIGNED_NOT_RUNTIME_PROOF" if aligned
                   else "SOURCE_CONTRACT_CHANGED_REVIEW_REQUIRED",
        "regional_snapshot_dmn_install_observed": False,
        "android_network_install_verified": False,
        "dmn_graph_compatibility_verified": False,
        "offline_route_to_screen_voice_verified": False,
        "real_device_behavior": "unknown",
        "release_allowed": False,
        "interpretation": "The source calls DMN for road pieces, routing and conservative matching; no network or legal-road acceptance is inferred.",
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
    print("M02 DMN source check:", result["finding"], "- offline acceptance NOT verified")


if __name__ == "__main__":
    main()
