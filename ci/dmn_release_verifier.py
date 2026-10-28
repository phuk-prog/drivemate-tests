"""TF-06: DriveMate Navigation (DMN) is the primary navigation road-pack consumer.

Reuse existing metadata verification; add explicit source-to-consumer gates
and bounded readback of exact DMN road-piece bytes. Valhalla routing-uk is a
distinct optional/offline matching dependency and not a substitute for DMN.
No metadata-only success is an actual route, verified UK coverage or a legal
redistribution-rights determination.
"""
import gzip
import hashlib
import json
from pathlib import Path
import re

from category1_national_release import verify_dmn

DMN_REPO="phuk-prog/drivemate-map"
HEX=re.compile(r"[0-9a-f]{64}\Z")


def check(ok,code):
    if not ok:
        raise ValueError(code)


def app_consumer_contract(dmn_tiles,source,gradle):
    """Source wiring only. Build-time overrides and installed APK need TF-03."""
    check(all(isinstance(s,str) for s in (dmn_tiles,source,gradle)),
          "DMN_APP_SOURCE_MISSING")
    check('MapReleaseSource.base' in dmn_tiles
          and '"$RELEASES/dmn-uk"' in dmn_tiles
          and 'private const val INDEX = "dmn-uk.json"' in dmn_tiles,
          "DMN_APP_SOURCE_NOT_ALIGNED")
    check('DmnPack.merge(' in dmn_tiles and 'DmnRouter(' in dmn_tiles
          and 'fun fetchAll(' in dmn_tiles
          and 'piece.sha256' in dmn_tiles
          and 'val idx = index(dir)' in dmn_tiles,
          "DMN_ROUTE_CONSUMER_UNVERIFIED")
    check("BuildConfig.MAP_RELEASES_BASE_URL" in source,
          "DMN_MAP_RELEASE_CONFIG_UNVERIFIED")
    check('https://github.com/phuk-prog/drivemate-map/releases/download' in gradle,
          "DMN_DEFAULT_PUBLISHER_MISMATCH")
    return {
        "schema":1,"scope":"checked_out_dmn_consumer_source_only",
        "engine":"DriveMate Navigation (DMN)",
        "release_repository_default":DMN_REPO,
        "source_wiring_verified":True,
        "compiled_override_or_device_endpoint_verified":False,
        "routing_functional_verified":False,
        "offline_navigation_accepted":False,
    }


def manifest_pieces(raw):
    check(isinstance(raw,bytes) and 0<len(raw)<=4*1024*1024,
          "DMN_POINTER_BYTES_INVALID")
    try:
        doc=json.loads(raw)
    except (ValueError,UnicodeDecodeError):
        raise ValueError("DMN_POINTER_JSON_INVALID") from None
    check(isinstance(doc,dict) and doc.get("schema")==1 and
          doc.get("format")=="DMNPACK2" and
          isinstance(doc.get("release"),str) and
          re.fullmatch(r"dmn-uk-\d{8}(?:-\d+)?",doc["release"]),
          "DMN_APP_SCHEMA_INCOMPATIBLE")
    check(isinstance(doc.get("tiles"),dict) and bool(doc["tiles"])
          and isinstance(doc.get("regions",{}),dict)
          and isinstance(doc.get("main"),dict),
          "DMN_PIECES_MISSING")
    names={}
    count=0
    for source in (doc["tiles"],doc.get("regions",{})):
        for key,row in source.items():
            check(isinstance(key,str) and isinstance(row,dict),
                  "DMN_PIECE_INVALID")
            count+=1
            name=row.get("file")
            check(isinstance(name,str) and re.fullmatch(r"dmn-[A-Za-z0-9_-]+\.dmnpack",name),
                  "DMN_PART_NAME_INVALID")
            check(name not in names,"DMN_DUPLICATE_PIECE")
            check(type(row.get("bytes")) is int and 0<row["bytes"]<=200*1024*1024
                  and isinstance(row.get("sha256"),str) and HEX.fullmatch(row["sha256"]),
                  "DMN_PIECE_DESCRIPTOR_INVALID")
            names[name]=row
    main=doc["main"]
    name=main.get("file")
    check(name=="dmn-main-uk.dmnpack" and name not in names,"DMN_MAIN_INVALID")
    check(type(main.get("bytes")) is int and 0<main["bytes"]<=200*1024*1024
          and isinstance(main.get("sha256"),str) and HEX.fullmatch(main["sha256"]),
          "DMN_MAIN_INVALID")
    names[name]=main
    return doc,names


def release_contract(raw,rolling,dated,app_source=None):
    """Metadata only; app_source is verified source contract or absent."""
    doc,pieces=manifest_pieces(raw)
    existing=verify_dmn(raw,rolling,dated)
    check(existing["pieces_verified"]==len(pieces),"DMN_RELEASE_COUNT_MISMATCH")
    consumer=app_source if app_source is not None else {"source_wiring_verified":False}
    check(isinstance(consumer,dict),"DMN_CONSUMER_INVALID")
    aligned=consumer.get("source_wiring_verified") is True and \
            consumer.get("release_repository_default")==DMN_REPO
    return {
        "schema":1,"scope":"published_dmn_pointer_release_metadata_only",
        "engine":"DriveMate Navigation (DMN)",
        "published_release":doc["release"],
        "release_repository":DMN_REPO,
        "pieces_verified_metadata":len(pieces),
        "source_consumer_wiring_verified":aligned,
        "downloaded_bytes_verified":False,
        "dmnpack2_parse_verified":False,
        "legal_rights_status":"unknown_not_established_by_index",
        "uk_road_completeness":"unknown",
        "offline_navigation_accepted":False,
    }


def verify_local_pieces(pointer, directory):
    """Read real local bytes without downloading them; validate every pinned piece.

    Requires entire catalogue; one missing piece blocks complete UK data readback.
    This is gzip + DMNPACK2 magic, not full Kotlin DmnPack.read or route.
    """
    _,pieces=manifest_pieces(pointer)
    base=Path(directory)
    check(base.is_dir() and not base.is_symlink(),"DMN_DIR_INVALID")
    total=0
    for name,row in pieces.items():
        path=base/name
        check(path.is_file() and not path.is_symlink(),"DMN_PART_MISSING:"+name)
        check(path.stat().st_size==row["bytes"],"DMN_PART_BYTES_MISMATCH:"+name)
        h=hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda:stream.read(1024*1024),b""):
                h.update(block)
        check(h.hexdigest()==row["sha256"],"DMN_PART_HASH_MISMATCH:"+name)
        try:
            with gzip.open(path,"rb") as stream:
                magic=stream.read(8)
        except (OSError,EOFError):
            raise ValueError("DMN_PART_GZIP_INVALID:"+name) from None
        check(magic==b"DMNPACK2","DMN_PART_FORMAT_INCOMPATIBLE:"+name)
        total+=row["bytes"]
    return {
        "schema":1,"scope":"local_dmn_files_exact_bytes_gzip_magic_only",
        "engine":"DriveMate Navigation (DMN)","pieces_verified":len(pieces),
        "compressed_bytes_verified":total,"actual_local_bytes_verified":True,
        "android_production_parser_verified":False,
        "offline_route_verified":False,"legal_rights_status":"unknown",
        "uk_road_completeness":"unknown","offline_navigation_accepted":False,
    }
