"""Genuine UK offline navigation acceptance gates; NEVER infer passing stages.

Display PMTiles, downloaded UK routing graph, offline transport and UI are
separate real-world stages. This file validates evidence status; it does not
synthesize road layouts, legal traffic rules or Android instrumentation output.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

# These are distinct current source-backed locations: the published map workflow
# targets its own repository, while the shipped app still requests the old one.
APP_TARGET_REPO = "phuk-prog/drivemate-data"
PUBLISHER_REPO = "phuk-prog/drivemate-map"
ROUTING_TAG = "routing-uk"

STAGES=(
    "published_catalog_hash_verified",
    "region_pmtiles_bytes_verified",
    "app_region_cache_install_verified",
    "network_isolation_before_and_after",
    "routing_graph_public_release_verified",
    "graph_installed_in_production_store",
    "production_offline_route_computed",
    "production_guidance_screen_rendered",
    "independent_route_truth_verified",
)


def evaluate(evidence):
    if not isinstance(evidence,dict):
        raise ValueError("INVALID_OFFLINE_EVIDENCE")
    unknown=[name for name in STAGES if evidence.get(name) is None]
    failed=[name for name in STAGES if evidence.get(name) is False]
    for name in STAGES:
        value=evidence.get(name)
        if value is not None and type(value) is not bool:
            raise ValueError("INVALID_OFFLINE_STAGE:"+name)
    accepted=not unknown and not failed
    return {"schema":1,"scope":"real_published_uk_offline_acceptance",
            "status":"passed" if accepted else "blocked",
            "accepted":accepted,"stages":{k:evidence.get(k) for k in STAGES},
            "unknown_stages":unknown,"failed_stages":failed,
            "note":"Map-display file hashes alone do not prove installed offline routing or road law."}


def release_readiness(status_code,assets=None):
    if status_code in (401,403,404):
        # 404 can mean nonexistent, private or not authorized: cannot infer which.
        return {"status":"unavailable_or_inaccessible","public_verified":False,
                "confirmed_nonexistent":False}
    if status_code!=200:
        return {"status":"lookup_error","public_verified":False,
                "confirmed_nonexistent":False}
    if not isinstance(assets,list):
        return {"status":"invalid_release_metadata","public_verified":False,
                "confirmed_nonexistent":False}
    matches=[x for x in assets if isinstance(x,dict) and x.get("name")=="routing-uk.json"]
    if len(matches)!=1:
        return {"status":"manifest_missing","public_verified":False,
                "confirmed_nonexistent":False}
    a=matches[0]
    sha=a.get("digest")
    if (type(a.get("size")) is not int or a["size"]<1 or a["size"]>256*1024
        or not isinstance(sha,str) or not re.fullmatch(r"sha256:[a-f0-9]{64}",sha)):
        return {"status":"manifest_unverified","public_verified":False,
                "confirmed_nonexistent":False}
    # This is a release *pointer*, not downloaded and hash-checked graph bytes.
    return {"status":"public_release_metadata_verified","public_verified":True,
            "confirmed_nonexistent":False,
            "manifest_asset_bytes":a["size"],"manifest_asset_sha256":sha[7:]}


def public_release_lookup(repo,tag):
    # No client secrets, no redirects to untrusted URLs or arbitrary user input.
    if repo not in {APP_TARGET_REPO, PUBLISHER_REPO} or tag!=ROUTING_TAG:
        raise ValueError("UNTRUSTED_RELEASE_TARGET")
    args=["gh","api","-i","repos/"+repo+"/releases/tags/"+tag]
    try:
        proc=subprocess.run(args,stdin=subprocess.DEVNULL,capture_output=True,
                            timeout=30,check=False,text=True)
    except (OSError,subprocess.TimeoutExpired):
        return {"status":"lookup_error","public_verified":False,
                "confirmed_nonexistent":False}
    # Header fragments may include several status lines for proxy redirects.
    statuses=re.findall(r"^HTTP/\S+\s+(\d{3})\b",proc.stdout,re.M)
    status=int(statuses[-1]) if statuses else (404 if proc.returncode else 200)
    if status!=200:
        return release_readiness(status)
    if proc.returncode or len(proc.stdout)>2_000_000:
        return release_readiness(0)
    pos=proc.stdout.find("{")
    try:
        data=json.loads(proc.stdout[pos:])
    except (ValueError,TypeError):
        return release_readiness(0)
    if data.get("tag_name")!=tag:
        return release_readiness(0)
    return release_readiness(200,data.get("assets"))


def release_target_inventory():
    """Inspect each known release owner separately; metadata is not graph installation.

    Only these two code-reviewed repositories can be queried. Even a 200 release
    with a manifest hash does not verify downloaded bytes or an Android route.
    """
    app = public_release_lookup(APP_TARGET_REPO, ROUTING_TAG)
    publisher = public_release_lookup(PUBLISHER_REPO, ROUTING_TAG)
    return {
        "scope": "two_public_github_release_tag_metadata_lookups_only",
        "app_target_repository": APP_TARGET_REPO,
        "publisher_repository": PUBLISHER_REPO,
        "release_tag": ROUTING_TAG,
        "source_repository_mismatch": APP_TARGET_REPO != PUBLISHER_REPO,
        "app_target": app,
        "publisher_target": publisher,
        "app_graph_download_verified": False,
        "offline_acceptance": False,
        "limitations": "No manifest byte readback, app network request, installed routing graph, screen or legal-road truth",
    }


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",required=True)
    a=parser.parse_args()
    targets=release_target_inventory()
    public=targets["app_target"]
    # The gate does not import CI's previous map evidence as current app evidence;
    # every stage stays unknown until produced from the same Android test run.
    stages={name:None for name in STAGES}
    # Publication metadata cannot prove the actual graph bytes. A missing
    # release is False; a visible metadata-only release remains UNKNOWN.
    stages["routing_graph_public_release_verified"]=(
        None if public["public_verified"] else False)
    verdict=evaluate(stages)
    record={"schema":1,"published_graph_release":public,
            "publisher_graph_release":targets["publisher_target"],
            "routing_release_targets":targets,
            "source":"github_public_api_live",
            "offline_acceptance":verdict}
    p=Path(a.output)
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(record,sort_keys=True,indent=2)+"\n")
    print(json.dumps({"published_graph_release":public["status"],
                      "publisher_graph_release":targets["publisher_target"]["status"],
                      "real_offline_acceptance":verdict["status"],
                      "unknown_stages":len(verdict["unknown_stages"])},sort_keys=True))
    # Diagnosis job may PASS for an honest BLOCKED; do not return acceptance true.
    # A separate actual offline acceptance job must fail until all stages exist.


if __name__=="__main__":
    main()
