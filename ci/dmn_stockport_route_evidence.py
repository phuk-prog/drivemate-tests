"""Authenticated, bounded Android route-computation evidence from one real Stockport DMN tile.

A JUnit pass plus this receipt supports only a source-to-DmnRouter.plan component
observation. It is NOT a whole-UK drive, worker installation, offline network
isolation, guidance-screen, speech, road-law or release acceptance.
"""
import hashlib
import json
import math
from pathlib import Path


def _sha(path):
    h=hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            h.update(block)
    return h.hexdigest()


def verify(receipt_file, index_file, piece_file):
    receipt_path, index_path, piece_path = map(Path, (receipt_file,index_file,piece_file))
    paths=(receipt_path,index_path,piece_path)
    if any(p.is_symlink() or not p.is_file() for p in paths):
        raise ValueError("DMN_ROUTE_EVIDENCE_MISSING_OR_SYMLINK")
    sizes=[p.stat().st_size for p in paths]
    if not (0<sizes[0]<=4096 and 0<sizes[1]<=4*1024*1024 and 0<sizes[2]<=200*1024*1024):
        raise ValueError("DMN_ROUTE_SOURCE_OR_RECEIPT_SIZE_INVALID")
    try:
        receipt=json.loads(receipt_path.read_text(encoding="utf-8"))
        idx=json.loads(index_path.read_text(encoding="utf-8"))
        tile=idx["tiles"]["213_-9"]
    except (ValueError,TypeError,UnicodeError,KeyError):
        raise ValueError("DMN_ROUTE_INVALID_JSON") from None
    if not all(isinstance(x,dict) for x in (receipt,idx,tile)):
        raise ValueError("DMN_ROUTE_INVALID_SHAPE")
    if idx.get("schema")!=1 or idx.get("format")!="DMNPACK2":
        raise ValueError("DMN_ROUTE_PUBLISHED_FORMAT_INVALID")
    if (type(tile.get("bytes")) is not int or tile["bytes"]!=sizes[2] or
        not isinstance(tile.get("sha256"),str) or tile["sha256"]!=_sha(piece_path) or
        not isinstance(tile.get("file"),str) or not tile["file"].endswith(".dmnpack")):
        raise ValueError("DMN_ROUTE_PUBLISHED_TILE_MISMATCH")
    expected={
        "schema":1,
        "issue":"CAT1-PUBLISHED-DMN-ROUTE-01",
        "scope":"published_stockport_single_piece_android_router_plan_only",
        "tile_key":"213_-9",
        "published_release":idx["release"],
        "installed_sha256":tile["sha256"],
        "installed_bytes":tile["bytes"],
        "start_lat":53.4051,"start_lon":-2.1630,
        "finish_lat":53.4106,"finish_lon":-2.1591,
        "route_contiguous_and_turns_checked":True,
        "dmn_route_computed":True,
        "map_pack_worker_installed":False,
        "whole_uk_route_verified":False,
        "device_network_isolated":False,
        "guidance_screen_verified":False,
        "speech_verified":False,
        "independent_legal_truth_verified":False,
        "release_allowed":False,
    }
    if any(type(receipt.get(k)) is not type(v) or receipt.get(k)!=v for k,v in expected.items()):
        raise ValueError("DMN_ROUTE_SCOPE_OR_SOURCE_MISMATCH")
    for key in ("node_count","edge_count","start_edge","finish_edge","planned_edges"):
        if type(receipt.get(key)) is not int:
            raise ValueError("DMN_ROUTE_COUNT_OR_INDEX_INVALID")
    nodes,edges=receipt["node_count"],receipt["edge_count"]
    start,finish,count=(receipt[k] for k in ("start_edge","finish_edge","planned_edges"))
    if not (nodes>0 and edges>0 and 0<=start<edges and 0<=finish<edges and 0<count<=edges):
        raise ValueError("DMN_ROUTE_EMPTY_OR_OUT_OF_RANGE")
    for key in ("planned_metres","planned_seconds"):
        value=receipt.get(key)
        if type(value) not in (int,float) or not math.isfinite(value) or value<=0:
            raise ValueError("DMN_ROUTE_LENGTH_OR_TIME_INVALID")
    return {
        "case_id":expected["issue"],
        "scope":expected["scope"],
        "tile_key":"213_-9",
        "published_release":idx["release"],
        "published_index_sha256":_sha(index_path),
        "installed_sha256":tile["sha256"],
        "installed_bytes":tile["bytes"],
        "node_count":nodes,"edge_count":edges,
        "start_edge":start,"finish_edge":finish,
        "planned_edges":count,
        "planned_metres":receipt["planned_metres"],
        "planned_seconds":receipt["planned_seconds"],
        "production_dmn_route_computed":True,
        "map_pack_worker_installed":False,
        "network_isolated":False,
        "whole_uk_route_verified":False,
        "guidance_screen_verified":False,
        "speech_verified":False,
        "independent_legal_truth_verified":False,
        "release_allowed":False,
    }
