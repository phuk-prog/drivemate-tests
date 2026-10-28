"""Category 1: fail-closed metadata checks for actual published UK map and DMN assets.

Verifies GitHub release metadata and small rolling JSON pointers only. It does
NOT download graph/map bytes, prove licensing, legal roads or Android guidance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

REPO = "phuk-prog/drivemate-map"
HEX = re.compile(r"[0-9a-f]{64}\Z")
DMN_TAG = re.compile(r"dmn-uk-[0-9]{8}(?:-[0-9]+)?\Z")
MAP_TAG = re.compile(r"map-data-ukmap-r[0-9]{1,20}-a[0-9]{1,6}\Z")
DMN_FILE = re.compile(r"dmn-[A-Za-z0-9_-]+\.dmnpack\Z")
MAX_PIECE = 200 * 1024 * 1024


def require(condition, code):
    if not condition:
        raise ValueError(code)


def one_asset(release, tag, name):
    require(isinstance(release, dict) and release.get("tag_name") == tag,
            "RELEASE_TAG_MISMATCH")
    assets = release.get("assets")
    require(isinstance(assets, list), "RELEASE_ASSETS_MISSING")
    matches = [x for x in assets if isinstance(x, dict) and x.get("name") == name]
    require(len(matches) == 1, "RELEASE_ASSET_MISSING_OR_DUPLICATED")
    return matches[0]


def verify_pointer(raw, rolling, tag, name, maximum):
    require(isinstance(raw, bytes) and 0 < len(raw) <= maximum,
            "POINTER_BYTES_INVALID")
    a = one_asset(rolling, tag, name)
    require(type(a.get("size")) is int and a["size"] == len(raw)
            and a.get("digest") == "sha256:" + hashlib.sha256(raw).hexdigest(),
            "POINTER_RELEASE_DIGEST_MISMATCH")
    try:
        doc = json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        raise ValueError("POINTER_JSON_INVALID") from None
    require(isinstance(doc, dict) and doc.get("schema") == 1,
            "POINTER_SCHEMA_INVALID")
    return doc


def verify_dmn(raw, rolling, dated):
    doc = verify_pointer(raw, rolling, "dmn-uk", "dmn-uk.json", 4 * 1024 * 1024)
    tag = doc.get("release")
    require(doc.get("format") == "DMNPACK2" and isinstance(tag, str)
            and DMN_TAG.fullmatch(tag), "DMN_APP_SCHEMA_INCOMPATIBLE")
    require(isinstance(doc.get("tiles"), dict) and bool(doc["tiles"]),
            "DMN_TILES_MISSING")
    require(isinstance(doc.get("regions", {}), dict), "DMN_REGIONS_INVALID")
    files = []
    for section in ("tiles", "regions"):
        for key, value in doc.get(section, {}).items():
            require(isinstance(key, str) and isinstance(value, dict),
                    "DMN_PIECE_INVALID")
            files.append(value)
    require(isinstance(doc.get("main"), dict), "DMN_MAIN_MISSING")
    files.append(doc["main"])
    names = []
    for p in files:
        name, size, digest = p.get("file"), p.get("bytes"), p.get("sha256")
        require(isinstance(name, str) and DMN_FILE.fullmatch(name)
                and type(size) is int and 0 < size <= MAX_PIECE
                and isinstance(digest, str) and HEX.fullmatch(digest),
                "DMN_PIECE_APP_INCOMPATIBLE")
        names.append(name)
        a = one_asset(dated, tag, name)
        require(a.get("size") == size and a.get("digest") == "sha256:" + digest,
                "DMN_PIECE_RELEASE_MISMATCH")
    require(len(names) == len(set(names)), "DMN_DUPLICATE_PIECE")
    assets = dated.get("assets", [])
    require(len(assets) == len(names)
            and set(x.get("name") for x in assets if isinstance(x, dict)) == set(names),
            "DMN_RELEASE_ASSET_SET_MISMATCH")
    return {"scope": "release_metadata_only", "pointer_verified": True,
            "dated_tag": tag, "pieces_verified": len(names),
            "actual_piece_bytes_downloaded": False,
            "uk_legal_road_truth": "unknown",
            "distribution_rights": "unknown",
            "offline_navigation_accepted": False}


def verify_ukmap(raw, rolling, dated):
    doc = verify_pointer(raw, rolling, "map-data-ukmap", "ukmap.json", 1024 * 1024)
    m = doc.get("map")
    require(isinstance(m, dict), "UKMAP_POINTER_INVALID")
    tag = m.get("tag")
    require(isinstance(tag, str) and MAP_TAG.fullmatch(tag)
            and m.get("name") == "drivemate-v1.pmtiles"
            and m.get("layout") == "drivemate-v1"
            and isinstance(m.get("sha256"), str) and HEX.fullmatch(m["sha256"])
            and type(m.get("bytes")) is int and 0 < m["bytes"] <= 2_000_000_000,
            "UKMAP_APP_SCHEMA_INCOMPATIBLE")
    a = one_asset(dated, tag, "drivemate-v1.pmtiles")
    require(a.get("size") == m["bytes"]
            and a.get("digest") == "sha256:" + m["sha256"],
            "UKMAP_RELEASE_MISMATCH")
    return {"scope": "release_metadata_only", "pointer_verified": True,
            "dated_tag": tag, "assets_verified": 1,
            "actual_map_bytes_downloaded": False,
            "uk_legal_road_truth": "unknown",
            "distribution_rights": "unknown",
            "offline_navigation_accepted": False}


def gh_json(tag):
    args = ["gh", "api", "repos/" + REPO + "/releases/tags/" + tag]
    result = subprocess.run(args, check=True, capture_output=True, timeout=60)
    require(len(result.stdout) <= 8_000_000, "RELEASE_METADATA_TOO_LARGE")
    return json.loads(result.stdout)


def gh_pointer(tag, name, directory):
    subprocess.run(["gh", "release", "download", tag, "-R", REPO,
                    "-p", name, "-D", str(directory)], check=True,
                   capture_output=True, timeout=120)
    data = (directory / name).read_bytes()
    require(len(data) <= 4 * 1024 * 1024, "POINTER_TOO_LARGE")
    return data


def audit_live():
    with tempfile.TemporaryDirectory() as folder:
        directory = Path(folder)
        rolling_dmn = gh_json("dmn-uk")
        raw_dmn = gh_pointer("dmn-uk", "dmn-uk.json", directory)
        dmn_doc = json.loads(raw_dmn)
        require(isinstance(dmn_doc.get("release"), str)
                and DMN_TAG.fullmatch(dmn_doc["release"]),
                "DMN_APP_SCHEMA_INCOMPATIBLE")
        dated_dmn = gh_json(dmn_doc["release"])
        dmn = verify_dmn(raw_dmn, rolling_dmn, dated_dmn)
        rolling_map = gh_json("map-data-ukmap")
        raw_map = gh_pointer("map-data-ukmap", "ukmap.json", directory)
        map_doc = json.loads(raw_map)
        tag = map_doc.get("map", {}).get("tag")
        require(isinstance(tag, str) and MAP_TAG.fullmatch(tag),
                "UKMAP_APP_SCHEMA_INCOMPATIBLE")
        dated_map = gh_json(tag)
        ukmap = verify_ukmap(raw_map, rolling_map, dated_map)
    return {"schema": 1, "status": "passed", "source": "github_live_release_metadata",
            "dmn": dmn, "ukmap": ukmap,
            "routing_uk_valhalla_graph_verified": False,
            "offline_navigation_accepted": False}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--live", action="store_true", required=True)
    p.add_argument("--output", required=True)
    a = p.parse_args()
    report = audit_live()
    out = Path(a.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"status": report["status"],
                      "dmn_pieces_metadata": report["dmn"]["pieces_verified"],
                      "ukmap_assets_metadata": report["ukmap"]["assets_verified"],
                      "offline_navigation_accepted": False}, sort_keys=True))


if __name__ == "__main__":
    main()
