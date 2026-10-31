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
import subprocess

OUT_OF_UK = {"isle-of-man", "guernsey-jersey"}
PROBES = {"England": "greater-manchester", "Scotland": "scotland",
          "Wales": "wales", "Northern Ireland": "northern-ireland-area"}
HEX64 = re.compile(r"[a-f0-9]{64}\Z")
REGION_ID = re.compile(r"[a-z0-9][a-z0-9-]{0,70}\Z")


def verify_asset(data, release):
    # Remote release JSON is untrusted. A malformed asset list must produce a
    # recorded failed audit, not an uncaught AttributeError before output JSON.
    if not isinstance(release, dict) or not isinstance(release.get("assets"), list):
        raise ValueError("CATALOG_RELEASE_ASSET_INDEX_INVALID")
    if any(not isinstance(a, dict) for a in release["assets"]):
        raise ValueError("CATALOG_RELEASE_ASSET_INDEX_INVALID")
    assets = [a for a in release["assets"] if a.get("name") == "catalog.json"]
    if len(assets) != 1:
        raise ValueError("CATALOG_ASSET_MISSING_OR_DUPLICATED")
    asset = assets[0]
    digest = hashlib.sha256(data).hexdigest()
    if type(asset.get("size")) is not int or asset["size"] != len(data) or asset.get("digest") != "sha256:" + digest:
        raise ValueError("CATALOG_RELEASE_DIGEST_MISMATCH")
    if release.get("tag_name") != "map-regions":
        raise ValueError("UNEXPECTED_CATALOG_RELEASE")
    return {"asset_id": asset.get("id"), "bytes": len(data), "sha256": digest,
            "release_tag": release["tag_name"], "published_at": release.get("published_at")}


def audit_catalog(catalog):
    if not isinstance(catalog, dict) or type(catalog.get("schema")) is not int or catalog["schema"] != 1 or catalog.get("coverage") != "partial":
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



def verify_region_release_inventory(catalog, fetch_release):
    """Cross-check immutable regional pointers against GitHub release metadata.

    Only GitHub-advertised size and SHA-256 digests, not downloaded PMTiles
    bytes or a verified installed navigation asset. Each release tag is
    fetched at most once. Each region's map and manifest may use different
    immutable tags because the publisher reuses unchanged map assets.
    """
    entries=catalog.get("regions", {})
    if not isinstance(entries, dict) or not entries:
        raise ValueError("REGIONAL_RELEASE_INDEX_MISSING")
    resolved={}
    verified=0
    for region, info in sorted(entries.items()):
        if not isinstance(info, dict):
            raise ValueError("REGIONAL_RELEASE_POINTER_INVALID")
        for kind, filename in (("map", "drivemate-v1.pmtiles"),
                               ("manifest", "manifest.json")):
            pointer=info.get(kind)
            if not isinstance(pointer, dict):
                raise ValueError("REGIONAL_RELEASE_POINTER_INVALID")
            tag=pointer.get("tag")
            if (not isinstance(tag, str) or len(tag)>180
                or not re.fullmatch(r"[a-z0-9-]+", tag)
                or not tag.startswith("map-data-region-" + region + "-")):
                raise ValueError("REGIONAL_RELEASE_POINTER_INVALID")
            if tag not in resolved:
                try:
                    release=fetch_release(tag)
                except (KeyError, OSError, subprocess.CalledProcessError,
                        subprocess.TimeoutExpired, ValueError):
                    # Tag is regex-validated above: safe public diagnostic, no token/URL leakage.
                    raise ValueError(f"REGIONAL_RELEASE_LOOKUP_FAILED tag={tag}") from None
                if not isinstance(release, dict):
                    raise ValueError("REGIONAL_RELEASE_LOOKUP_FAILED")
                resolved[tag]=release
            release=resolved[tag]
            if release.get("tag_name")!=tag:
                raise ValueError("REGIONAL_RELEASE_TAG_MISMATCH")
            assets=release.get("assets")
            if not isinstance(assets, list):
                raise ValueError("REGIONAL_RELEASE_ASSET_MISSING")
            matches=[asset for asset in assets
                     if isinstance(asset,dict) and asset.get("name")==filename]
            if len(matches)>1:
                raise ValueError("REGIONAL_RELEASE_ASSET_DUPLICATE")
            if not matches:
                raise ValueError("REGIONAL_RELEASE_ASSET_MISSING")
            asset=matches[0]
            if (type(asset.get("size")) is not int
                or asset.get("size") != pointer.get("bytes")
                or asset.get("digest") != "sha256:" + str(pointer.get("sha256",""))):
                raise ValueError("REGIONAL_RELEASE_ASSET_MISMATCH")
            verified+=1
    return {"scope":"release_asset_metadata_only",
            "regions_checked":len(entries),"assets_verified":verified,
            "release_tags_checked":len(resolved),
            "actual_map_bytes_downloaded":"unknown",
            "offline_navigation_acceptance":"unknown"}


def github_release(tag):
    """Read only published release metadata; never download multi-GB PMTiles."""
    cmd=["gh","api","repos/phuk-prog/drivemate-map/releases/tags/"+tag]
    try:
        result=subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                              check=False,timeout=30)
        if result.returncode or len(result.stdout)>8_000_000:
            raise ValueError("REGIONAL_RELEASE_LOOKUP_FAILED")
        return json.loads(result.stdout)
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
        raise ValueError("REGIONAL_RELEASE_LOOKUP_FAILED") from None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--catalog", required=True)
    p.add_argument("--release", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--verify-region-releases", action="store_true")
    a = p.parse_args()
    result = {"schema": 1, "audit": "cat1-live-catalog", "status": "failed"}
    try:
        raw = Path(a.catalog).read_bytes()
        release = json.loads(Path(a.release).read_text())
        result["published_asset"] = verify_asset(raw, release)
        catalog=json.loads(raw)
        result.update(audit_catalog(catalog))
        # Do not spend API calls proving regional pointers until the catalogue
        # itself satisfies the UK-scope and integrity gate.
        if a.verify_region_releases and result["status"]=="passed":
            result["region_release_inventory"]=verify_region_release_inventory(
                catalog, github_release)
        elif a.verify_region_releases:
            result["region_release_inventory"]={"status":"blocked_by_catalog_failure"}
    except (ValueError, TypeError, UnicodeError, json.JSONDecodeError, OSError) as e:
        result["status"] = "failed"
        result["failures"] = [str(e)]
    output = Path(a.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, sort_keys=True))
    if result["status"] != "passed":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
