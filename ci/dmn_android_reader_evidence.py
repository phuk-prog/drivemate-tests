"""Fail-closed evidence check for a real published DMN piece in the Android reader.

This is one-piece parser coverage only, not map-worker/download/route/voice/UK acceptance.
"""
import hashlib
import json
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(receipt_file, index_file, piece_file):
    paths=[Path(receipt_file),Path(index_file),Path(piece_file)]
    if any(p.is_symlink() or not p.is_file() for p in paths):
        raise ValueError("DMN_READER_SOURCE_OR_RECEIPT_MISSING")
    if not (0 < paths[0].stat().st_size <= 4096
            and 0 < paths[1].stat().st_size <= 4*1024*1024
            and 0 < paths[2].stat().st_size <= 200*1024*1024):
        raise ValueError("DMN_READER_FILE_SIZE_INVALID")
    try:
        record=json.loads(paths[0].read_text())
        idx=json.loads(paths[1].read_text())
        piece=idx["tiles"]["213_-9"]
    except (UnicodeError,ValueError,TypeError,KeyError):
        raise ValueError("DMN_READER_RECORD_INVALID") from None
    if idx.get("schema")!=1 or idx.get("format")!="DMNPACK2":
        raise ValueError("DMN_READER_SOURCE_FORMAT_INVALID")
    raw=paths[2].read_bytes()
    if (piece.get("sha256")!=digest(raw) or piece.get("bytes")!=len(raw)
        or not isinstance(piece.get("file"),str) or not piece["file"].endswith(".dmnpack")):
        raise ValueError("DMN_READER_PUBLISHED_SOURCE_MISMATCH")
    required={
        "schema":1,"issue":"CAT1-PUBLISHED-DMN-ANDROID-READER-01",
        "scope":"published_stockport_piece_android_parser_only",
        "tile_key":"213_-9","published_release":idx["release"],
        "installed_sha256":piece["sha256"],"installed_bytes":len(raw),
        "reader":"DmnPack.read + DmnTiles.router",
        "map_pack_worker_verified":False,"complete_uk_verified":False,
        "offline_route_computed":False,"guidance_screen_verified":False,
        "speech_verified":False,"independent_legal_truth_verified":False,
        "release_allowed":False
    }
    if any(type(record.get(k)) is not type(v) or record.get(k)!=v for k,v in required.items()):
        raise ValueError("DMN_READER_SCOPE_OR_INSTALLED_BYTES_MISMATCH")
    if (type(record.get("node_count")) is not int or record["node_count"]<=0 or
        type(record.get("edge_count")) is not int or record["edge_count"]<=0):
        raise ValueError("DMN_READER_EMPTY_GRAPH")
    return {
        "case_id":required["issue"],"scope":required["scope"],"tile_key":"213_-9",
        "published_release":idx["release"],"index_sha256":digest(paths[1].read_bytes()),
        "installed_sha256":piece["sha256"],"installed_bytes":len(raw),
        "node_count":record["node_count"],"edge_count":record["edge_count"],
        "production_reader_passed":True,
        "map_pack_worker_verified":False,"offline_route_computed":False,
        "guidance_screen_verified":False,"speech_verified":False,
        "independent_legal_truth_verified":False,"release_allowed":False
    }


def verify_install_route(receipt_file, index_file, piece_file):
    """Validate bounded Android downloader/installer evidence against published bytes."""
    path = Path(receipt_file)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= 4096:
        raise ValueError("DMN_INSTALL_RECEIPT_MISSING_OR_OVERSIZED")
    record = json.loads(path.read_text())
    idx = json.loads(Path(index_file).read_text())
    raw = Path(piece_file).read_bytes()
    piece = idx["tiles"]["213_-9"]
    expected = {
        "schema": 1,
        "scope": "published_stockport_subset_tls_download_install_offline_route",
        "published_release": idx["release"],
        "installed_sha256": digest(raw), "installed_bytes": len(raw),
        "download_calls": 3, "checksum_rejection_verified": True,
        "corruption_repair_verified": True, "offline_route_computed": True,
        "route_time_network_requests": 0,
        "complete_uk_verified": False, "independent_legal_truth_verified": False,
    }
    if piece.get("sha256") != digest(raw) or piece.get("bytes") != len(raw):
        raise ValueError("DMN_INSTALL_PUBLISHED_BYTES_MISMATCH")
    if any(type(record.get(k)) is not type(v) or record.get(k) != v for k,v in expected.items()):
        raise ValueError("DMN_INSTALL_SCOPE_OR_BYTES_MISMATCH")
    metres = record.get("route_metres")
    if (type(record.get("route_edges")) is not int or record["route_edges"] <= 0
            or type(metres) not in (int, float) or not 100 < metres < 100000):
        raise ValueError("DMN_INSTALL_ROUTE_EMPTY_OR_UNBOUNDED")
    return record
