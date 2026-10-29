"""Fail-closed bounded Android receipt validation for a candidate regional DMN worker.

The Android test downloads one genuine published UK road piece, not all UK packs.
It must never upgrade a one-piece READY claim into whole-UK/offline-journey success.
"""
import hashlib
import json
from pathlib import Path
import re

def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()

def verify(receipt_path,catalog_path,index_path):
    paths=[Path(receipt_path),Path(catalog_path),Path(index_path)]
    if any(not p.is_file() or p.is_symlink() or not 0<p.stat().st_size<=4*1024*1024 for p in paths):
        raise ValueError("REGIONAL_GREEN_EVIDENCE_MISSING")
    try:
        receipt=json.loads(paths[0].read_text())
        catalog=json.loads(paths[1].read_text())
        idx=json.loads(paths[2].read_text())
        stockport=catalog["regions"]["stockport"]["map"]
        tile=idx["tiles"]["213_-9"]
    except (KeyError,ValueError,TypeError,UnicodeError):
        raise ValueError("REGIONAL_GREEN_SOURCE_INVALID") from None
    if (catalog.get("test_source",{}).get("kind")!="actual_published_catalog_subset"
        or idx.get("schema")!=1 or idx.get("format")!="DMNPACK2"
        or not re.fullmatch(r"dmn-[A-Za-z0-9_-]+\.dmnpack",tile.get("file",""))
        or not re.fullmatch(r"[0-9a-f]{64}",tile.get("sha256",""))
        or not isinstance(tile.get("bytes"),int) or not 0<tile["bytes"]<200*1024*1024):
        raise ValueError("REGIONAL_GREEN_PUBLISHED_SOURCE_INVALID")
    required={
        "schema":1,"issue":"CAT1-REGIONAL-MAP-WORKER-GREEN-01",
        "scope":"same_stockport_region_real_worker_single_real_dmn_piece_candidate",
        "region_id":"stockport",
        "published_catalog_sha256":catalog["test_source"]["catalog_sha256"],
        "display_installed_sha256":stockport["sha256"],
        "display_installed_bytes":stockport["bytes"],
        "full_published_index_sha256":sha(paths[2]),
        "published_dmn_release":idx["release"],
        "published_piece_file":tile["file"],
        "published_piece_sha256":tile["sha256"],
        "published_piece_bytes":tile["bytes"],
        "installed_dmn_index_scope":"single_tile_bounded_subset",
        "routing_index_installed":True,
        "one_published_dmn_piece_installed":True,
        "dmn_offline_for_two_points":"READY",
        "worker_result":"SUCCESS",
        "whole_uk_install_verified":False,
        "offline_route_computed":False,
        "screen_or_speech_verified":False,
        "independent_legal_truth_verified":False,
        "release_allowed":False
    }
    if any(type(receipt.get(k)) is not type(v) or receipt.get(k)!=v for k,v in required.items()):
        raise ValueError("REGIONAL_GREEN_RECEIPT_MISMATCH")
    if (type(receipt.get("node_count")) is not int or receipt["node_count"]<=0
        or type(receipt.get("edge_count")) is not int or receipt["edge_count"]<=0):
        raise ValueError("REGIONAL_GREEN_EMPTY_GRAPH")
    return {
        "case_id":required["issue"],"scope":required["scope"],"region":"stockport",
        "display_sha256":stockport["sha256"],"display_bytes":stockport["bytes"],
        "published_index_sha256":sha(paths[2]),
        "published_release":idx["release"],
        "published_piece_sha256":tile["sha256"],"published_piece_bytes":tile["bytes"],
        "node_count":receipt["node_count"],"edge_count":receipt["edge_count"],
        "production_worker_graph_install_passed":True,
        "full_uk_install_verified":False,
        "offline_route_computed":False,"release_allowed":False
    }
