"""Read-only audit of the *live* map-regions release asset, not a source fixture.

Metadata presence is not road coverage, legal road truth, licensing clearance,
an installed map, or offline navigation acceptance.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

OUT_OF_UK = {"isle-of-man", "guernsey-jersey"}
PROBES = {"England": "greater-manchester", "Scotland": "scotland",
          "Wales": "wales", "Northern Ireland": "northern-ireland-area"}
HEX64 = re.compile(r"[a-f0-9]{64}\Z")
REGION_ID = re.compile(r"[a-z0-9][a-z0-9-]{0,70}\Z")


def verify_asset(data, release):
    assets = [a for a in release.get("assets", []) if a.get("name") == "catalog.json"]
    if len(assets) != 1:
        raise ValueError("CATALOG_ASSET_MISSING_OR_DUPLICATED")
    asset = assets[0]
    digest = hashlib.sha256(data).hexdigest()
    if asset.get("size") != len(data) or asset.get("digest") != "sha256:" + digest:
        raise ValueError("CATALOG_RELEASE_DIGEST_MISMATCH")
    if release.get("tag_name") != "map-regions":
        raise ValueError("UNEXPECTED_CATALOG_RELEASE")
    return {"asset_id": asset.get("id"), "bytes": len(data), "sha256": digest,
            "release_tag": release["tag_name"], "published_at": release.get("published_at")}


def audit_catalog(catalog):
    if not isinstance(catalog, dict) or catalog.get("schema") != 1 or catalog.get("coverage") != "partial":
        raise ValueError("INVALID_PARTIAL_CATALOG")
    regions = catalog.get("regions")
    if not isinstance(regions, dict):
        raise ValueError("INVALID_REGION_INDEX")
    malformed = []
    for key, item in regions.items():
        if not isinstance(key, str) or not REGION_ID.fullmatch(key) or not isinstance(item, dict) or item.get("id") != key:
            malformed.append(key)
            continue
        bounds = item.get("bounds")
        if (not isinstance(bounds, list) or len(bounds) != 4
                or any(not isinstance(x, (float, int)) or isinstance(x, bool) or not math.isfinite(x) for x in bounds)
                or not (-180 <= bounds[0] < bounds[2] <= 180 and -90 <= bounds[1] < bounds[3] <= 90)):
            malformed.append(key)
            continue
        for field in ("map", "manifest"):
            asset = item.get(field)
            if (not isinstance(asset, dict)
                    or not str(asset.get("tag", "")).startswith("map-data-region-" + key + "-")
                    or not HEX64.fullmatch(str(asset.get("sha256", "")))
                    or type(asset.get("bytes")) is not int or asset["bytes"] <= 0):
                malformed.append(key)
                break
    out_of_scope = sorted(OUT_OF_UK.intersection(regions))
    nations = {nation: key in regions for nation, key in PROBES.items()}
    problems = []
    if malformed:
        problems.append("MALFORMED_REGIONAL_POINTERS")
    if out_of_scope:
        problems.append("LIVE_CATALOG_NON_UK_REGIONS")
    if not all(nations.values()):
        problems.append("FOUR_NATION_PRESENCE_MISSING")
    ni = regions.get("northern-ireland-area")
    ni_source = ni.get("source_url", "") if isinstance(ni, dict) else ""
    ni_bounds = ni.get("bounds") if isinstance(ni, dict) else None
    return {"status": "failed" if problems else "passed", "failures": problems,
            "region_count": len(regions), "non_uk_regions": out_of_scope,
            "malformed_regions": sorted(set(malformed)),
            "four_nation_region_presence": nations,
            "northern_ireland_source": ni_source,
            "northern_ireland_bounds": ni_bounds,
            "northern_ireland_border_only": "unknown",
            "uk_geographic_and_attribute_coverage": "unknown",
            "distribution_rights": "unknown",
            "road_law_truth": "unknown",
            "offline_navigation_acceptance": "unknown",
            "claim": "published_catalog_metadata_only"}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--catalog", required=True)
    p.add_argument("--release", required=True)
    p.add_argument("--output", required=True)
    a = p.parse_args()
    result = {"schema": 1, "audit": "cat1-live-catalog", "status": "failed"}
    try:
        raw = Path(a.catalog).read_bytes()
        release = json.loads(Path(a.release).read_text())
        result["published_asset"] = verify_asset(raw, release)
        result.update(audit_catalog(json.loads(raw)))
    except (ValueError, TypeError, UnicodeError, json.JSONDecodeError) as e:
        result["failures"] = [str(e)]
    output = Path(a.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, sort_keys=True))
    if result["status"] != "passed":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
