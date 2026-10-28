"""Prepare one *actual published* UK region for Android's production map downloader.

Only the public CI runner fetches and verifies the full live release catalogue;
one unmodified Stockport region entry is copied into a test-only one-region
catalogue. Android RegionPacks.sync() then downloads the actual published map
through the production code. The subset is NOT fake UK-wide coverage.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from category1_live_catalog import verify_asset, audit_catalog


def one_region(full_bytes, release, key="stockport"):
    verified=verify_asset(full_bytes,release)
    doc=json.loads(full_bytes)
    scope=audit_catalog(doc)
    if scope["status"]!="passed":
        raise ValueError("LIVE_PUBLISHED_CATALOG_SCOPE_INVALID")
    item=doc["regions"].get(key)
    if not isinstance(item,dict) or item.get("id")!=key:
        raise ValueError("LIVE_REGION_NOT_IN_CATALOG")
    if not isinstance(item.get("map"),dict) or not isinstance(item.get("manifest"),dict):
        raise ValueError("LIVE_MAP_POINTER_MISSING")
    # Use exactly the published object, without rewriting any public source
    # roads, geometry, map labels, hashes or original immutable tags.
    return {"schema":1,"coverage":"partial","regions":{key:item},
            "test_source":{"kind":"actual_published_catalog_subset",
                           "region":key,"catalog_sha256":verified["sha256"],
                           "published_catalog_entries":scope["region_count"],
                           "official_map_display_only":True,
                           "offline_navigation_accepted":False}}


def run(out):
    out=Path(out)
    out.parent.mkdir(parents=True,exist_ok=True)
    with __import__("tempfile").TemporaryDirectory() as temp:
        directory=Path(temp)
        metadata=directory/"release.json"
        catalog=directory/"catalog.json"
        commands=[
            ["gh","api","repos/phuk-prog/drivemate-map/releases/tags/map-regions"],
            ["gh","release","download","map-regions","--repo","phuk-prog/drivemate-map",
             "--pattern","catalog.json","--dir",str(directory)]
        ]
        try:
            response=subprocess.run(commands[0],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                                    timeout=30,check=True)
            metadata.write_bytes(response.stdout)
            subprocess.run(commands[1],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL,timeout=60,check=True)
            result=one_region(catalog.read_bytes(),json.loads(metadata.read_bytes()))
        except (OSError,ValueError,subprocess.CalledProcessError,subprocess.TimeoutExpired):
            raise ValueError("PUBLISHED_STOCKPORT_SOURCE_UNAVAILABLE") from None
        out.write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
        print(json.dumps({"published_catalog_sha256":result["test_source"]["catalog_sha256"],
                          "test_region":"stockport",
                          "test_scope":"real_published_stockport_display_only"},sort_keys=True))


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",required=True)
    args=parser.parse_args()
    run(args.output)
