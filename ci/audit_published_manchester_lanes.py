#!/usr/bin/env python3
"""J27-01 Category 9.1: read *published* Manchester UK lane data, never synthetic roads.

This is a source-availability/quality pretest, NOT lane legality, GPS-to-lane
matching, UI, APK or actual driver-route acceptance. Public data only.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

BASE = "https://github.com/phuk-prog/drivemate-map/releases/download"
SQUARE = "lanes-106_-5.json"  # 53.0..53.5 N, -2.5..-2.0 E: Greater Manchester
TAG = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")
OUT = Path("results/cat3-published-lanes-audit.json")
SAMPLE_OUT = Path("results/cat3-published-manchester-samples.json")
# Actual Stockport/M60/Portwood geography; never claim this is the driver's route.
BOX = (53.37, 53.48, -2.29, -2.06)


def download(tag, name, limit):
    if not TAG.fullmatch(tag) or not re.fullmatch(r"[A-Za-z0-9._-]+", name):
        raise ValueError("invalid_publisher_asset_name")
    url = f"{BASE}/{quote(tag)}/{quote(name)}"
    request = Request(url, headers={"User-Agent": "DriveMate-public-Category3-audit"})
    with urlopen(request, timeout=35) as response:
        data = response.read(limit + 1)
    if not data or len(data) > limit:
        raise ValueError("published_asset_empty_or_too_large")
    return data


def checked_asset(metadata, name, max_bytes):
    expected_bytes = metadata["bytes"]
    expected_sha = metadata["sha256"]
    if not isinstance(expected_bytes, int) or not 1 <= expected_bytes <= max_bytes:
        raise ValueError("invalid_asset_length")
    if not isinstance(expected_sha, str) or not re.fullmatch("[0-9a-f]{64}", expected_sha):
        raise ValueError("invalid_asset_digest")
    data = download(metadata["tag"], name, max_bytes)
    if len(data) != expected_bytes or hashlib.sha256(data).hexdigest() != expected_sha:
        raise ValueError("asset_integrity_mismatch")
    return data


def touches_stockport(geometry):
    if not isinstance(geometry, list):
        return False
    south, north, west, east = BOX
    for point in geometry:
        if not isinstance(point, list) or len(point) < 2:
            continue
        try:
            lon, lat = float(point[0]), float(point[1])
        except (TypeError, ValueError):
            continue
        if south <= lat <= north and west <= lon <= east:
            return True
    return False


def lane_candidate(tags):
    # Not legal truth. Only a source-tagged multi-lane layout with nonempty arrows.
    if not isinstance(tags, dict):
        return False
    ways_oneway = tags.get("oneway") in ("yes", "1", "-1", "true") or tags.get("junction") in ("roundabout", "circular")
    keys = ("turn:lanes:forward", "turn:lanes:backward")
    if ways_oneway:
        keys += ("turn:lanes",)
    return any(
        isinstance(tags.get(key), str)
        and len(tags[key].split("|")) >= 2
        and all(part.strip() for part in tags[key].split("|"))
        for key in keys
    )


def audit():
    pointer_bytes = download("map-data-ukjoin", "uk.json", 1024 * 1024)
    pointer = json.loads(pointer_bytes)
    if pointer.get("schema") != 1 or not isinstance(pointer.get("manifest"), dict):
        raise ValueError("uk_pointer_invalid")
    manifest_bytes = checked_asset(pointer["manifest"], "manifest.json", 2 * 1024 * 1024)
    manifest = json.loads(manifest_bytes)
    if manifest.get("kind") != "uk-join" or manifest.get("schema") != 1:
        raise ValueError("uk_manifest_invalid")
    files = manifest.get("files", {})
    if SQUARE not in files:
        return {"status": "blocked", "reason": "manchester_lane_square_not_published",
                "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest()}
    square_bytes = checked_asset(files[SQUARE], SQUARE, 64 * 1024 * 1024)
    data = json.loads(square_bytes)
    ways = data.get("ways")
    if not isinstance(ways, list):
        raise ValueError("lane_square_structure_invalid")
    near = [w for w in ways if isinstance(w, dict) and touches_stockport(w.get("g"))]
    candidates = [w for w in near if lane_candidate(w.get("t"))]
    ids = [str(w.get("id", ""))[:60] for w in candidates[:12]]
    # Freeze a *subset of the actual published geometry and original tags* for
    # production LaneDb parser tests. No generated road, fabricated arrows or
    # inferred manoeuvre is permitted. The source digest binds the sample.
    samples = [
        {"id": w["id"], "t": w["t"], "g": w["g"]}
        for w in candidates if "id" in w and isinstance(w.get("g"), list)
        and len(w["g"]) >= 2
    ][:12]
    return {
        "status": "observed_source_data",
        "source": "OSM-derived published UK join, not independently verified road-law truth",
        "pointer_sha256": hashlib.sha256(pointer_bytes).hexdigest(),
        "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "lane_square_sha256": hashlib.sha256(square_bytes).hexdigest(),
        "lane_square": SQUARE,
        "total_ways_in_square": len(ways),
        "stockport_area_source_ways": len(near),
        "stockport_area_multi_lane_arrow_candidates": len(candidates),
        "candidate_osm_ids_sample": ids,
        "_sample_ways": samples,
        "legal_lane_truth": "unknown",
        "displayed_nav_ui_lanes": "not_tested",
        "immersive_junction_view": "not_tested",
        "driver_installed_apk": "unknown",
        "next": "Select corroborated source lane layout; full emulator NavigationSession/NavUi replay",
    }


def main():
    try:
        result = audit()
    except (HTTPError, URLError) as exc:
        result = {"status": "blocked", "reason": "publication_http_error",
                  "http_code": getattr(exc, "code", None)}
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        result = {"status": "blocked", "reason": "publication_integrity_or_format",
                  "detail": str(exc)[:100]}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sample = result.pop("_sample_ways", None)
    if sample is not None:
        SAMPLE_OUT.write_text(json.dumps({
            "schema": 1,
            "source": "actual published OSM-derived Manchester UK join",
            "source_sha256": result["lane_square_sha256"],
            "ways": sample,
        }, sort_keys=True, separators=(",", ":")) + "\n")
    OUT.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(json.dumps(result, sort_keys=True))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write("## Category 3 — published real Manchester lane source pretest\n\n")
            stream.write(f"Source status: **{result['status']}**. This is not an app lane-display pass.\n\n")
            if result["status"] == "observed_source_data":
                stream.write(f"Stockport-area source ways: **{result['stockport_area_source_ways']}**; ")
                stream.write(f"tagged multi-lane arrow candidates: **{result['stockport_area_multi_lane_arrow_candidates']}**. ")
                stream.write("Legality and visible on-screen guidance remain **unknown**.\n")
            else:
                stream.write(f"Blocked: **{result.get('reason', 'unknown')}**. No app behaviour verdict.\n")
    # A blocked data publication is an inconclusive source audit, not a green pass.
    return 0 if result["status"] == "observed_source_data" else 1


if __name__ == "__main__":
    sys.exit(main())
