"""Fail-closed report for a bounded real Android regional map-worker RED observation.

The fixture uses one real published Stockport display map, a deliberately empty
weekly companion manifest, and genuine published DMN index metadata.
This is NOT a full published-catalog offline/download acceptance test.
"""
import hashlib
import json
from pathlib import Path


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def verify(receipt_path, catalog_path, index_path):
    paths=[Path(receipt_path),Path(catalog_path),Path(index_path)]
    if any(p.is_symlink() or not p.is_file() for p in paths):
        raise ValueError("REGIONAL_WORKER_EVIDENCE_MISSING")
    if any(not 0 < p.stat().st_size <= 4*1024*1024 for p in paths):
        raise ValueError("REGIONAL_WORKER_EVIDENCE_SIZE_INVALID")
    try:
        receipt=json.loads(paths[0].read_text())
        catalog=json.loads(paths[1].read_text())
        index=json.loads(paths[2].read_text())
        stockport=catalog["regions"]["stockport"]["map"]
    except (ValueError,TypeError,UnicodeError,KeyError):
        raise ValueError("REGIONAL_WORKER_EVIDENCE_INVALID") from None
    if (catalog["test_source"].get("kind")!="actual_published_catalog_subset"
        or index.get("schema")!=1 or index.get("format")!="DMNPACK2"
        or not isinstance(index.get("tiles"),dict) or "213_-9" not in index["tiles"]):
        raise ValueError("REGIONAL_WORKER_SOURCE_INVALID")
    required={
        "schema":1,"issue":"CAT1-REGIONAL-MAP-WORKER-RED-01",
        "scope":"real_worker_one_published_stockport_display_bounded_empty_weekly_companions",
        "published_catalog_sha256":catalog["test_source"]["catalog_sha256"],
        "region_id":"stockport","display_installed_sha256":stockport["sha256"],
        "display_installed_bytes":stockport["bytes"],
        "published_dmn_index_sha256":_sha(paths[2].read_bytes()),
        "published_dmn_release":index["release"],
        "regional_snapshot_selected":True,
        "weekly_companion_files_in_bounded_fixture":0,
        "worker_result":"SUCCESS",
        "dmn_index_installed_after_worker":False,
        "dmn_offline_after_worker":"MISSING",
        "observed_bug":"REGIONAL_MAP_WORKER_SUCCESS_WITHOUT_DMN_GRAPH",
        "real_world_full_catalog_verified":False,
        "offline_navigation_verified":False,
        "screen_or_speech_verified":False,
        "release_allowed":False
    }
    if any(type(receipt.get(k)) is not type(v) or receipt.get(k)!=v for k,v in required.items()):
        raise ValueError("REGIONAL_WORKER_RECEIPT_MISMATCH")
    return {
        "case_id":required["issue"],"scope":required["scope"],
        "observed_bug":required["observed_bug"],"region":"stockport",
        "published_dmn_release":index["release"],
        "published_dmn_index_sha256":required["published_dmn_index_sha256"],
        "display_installed_sha256":stockport["sha256"],
        "display_installed_bytes":stockport["bytes"],
        "production_worker_observed":True,
        "dmn_graph_installed":False,
        "real_world_full_catalog_verified":False,
        "offline_navigation_verified":False,
        "release_allowed":False
    }
