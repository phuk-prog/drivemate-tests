"""TF-06: fail-closed Valhalla release contract, with separate byte/route proof.

This module only checks an authentic publisher manifest and release metadata
supplied by the caller, and optional *local* chunk bytes. It does not call
GitHub, produce/publish graph data, approve UK coverage or establish road-law correctness.
A test fixture validates a verifier, not a real public release.
"""
import gzip
import hashlib
import json
from datetime import datetime
from pathlib import Path
import re
import zlib

OWNER="phuk-prog/drivemate-map"
ROLLING_TAG="routing-uk"
HEX40=re.compile(r"[0-9a-f]{40}\Z")
HEX64=re.compile(r"[0-9a-f]{64}\Z")
GENERATION=re.compile(r"routing-uk-(\d{8})-r([1-9]\d*)-a([1-9]\d*)\Z")
MAX_GRAPH=64*1024**3
MAX_CHUNK=2_000_000_000


def check(condition, code):
    if not condition:
        raise ValueError(code)


def utc_date(value):
    try:
        check(isinstance(value,str) and len(value)<=45,"SOURCE_PROVENANCE_INCOMPLETE")
        date=datetime.fromisoformat(value.replace("Z","+00:00"))
        check(date.tzinfo is not None and date.utcoffset().total_seconds()==0,
              "SOURCE_PROVENANCE_INCOMPLETE")
        return date
    except (ValueError, TypeError, OverflowError, AttributeError):
        raise ValueError("SOURCE_PROVENANCE_INCOMPLETE") from None


def load_manifest(pointer):
    check(type(pointer) is bytes and 0<len(pointer)<=256*1024,"MANIFEST_BYTES_INVALID")
    try:
        doc=json.loads(pointer)
    except (ValueError, UnicodeDecodeError):
        raise ValueError("MANIFEST_JSON_INVALID") from None
    check(isinstance(doc,dict),"MANIFEST_JSON_INVALID")
    check(doc.get("engine")=="valhalla-3.6.3","GRAPH_ENGINE_INCOMPATIBLE")
    check(isinstance(doc.get("sha256"),str) and HEX64.fullmatch(doc["sha256"]),
          "GRAPH_DESCRIPTOR_INVALID")
    check(type(doc.get("bytes")) is int and 0<doc["bytes"]<=MAX_GRAPH,
          "GRAPH_DESCRIPTOR_INVALID")
    check(type(doc.get("download_bytes")) is int and 0<doc["download_bytes"]<=MAX_GRAPH,
          "GRAPH_DESCRIPTOR_INVALID")
    tag=doc.get("generation")
    check(isinstance(tag,str) and GENERATION.fullmatch(tag),"GENERATION_INVALID")
    day,run,attempt=GENERATION.fullmatch(tag).groups()
    check(isinstance(doc.get("build_run"),str) and doc["build_run"]==run
          and isinstance(doc.get("build_commit"),str)
          and HEX40.fullmatch(doc["build_commit"]), "BUILD_IDENTITY_MISMATCH")
    built=utc_date(doc.get("built"))
    check(built.strftime("%Y%m%d")==day,"BUILD_IDENTITY_MISMATCH")
    source=doc.get("source")
    check(isinstance(source,dict),"SOURCE_PROVENANCE_INCOMPLETE")
    check(isinstance(source.get("sha256"),str)
          and HEX64.fullmatch(source["sha256"])
          and isinstance(source.get("url"),str)
          and source["url"].startswith("https://download.geofabrik.de/europe/united-kingdom-")
          and source["url"].endswith(".osm.pbf"),
          "SOURCE_PROVENANCE_INCOMPLETE")
    retrieved=utc_date(source.get("retrieved_at"))
    check(retrieved<=built,"SOURCE_PROVENANCE_INCOMPLETE")
    parts=doc.get("parts")
    check(isinstance(parts,list) and 1<=len(parts)<=128 and
          all(isinstance(x,str) for x in parts),"INVALID_PART_NAME")
    prefix="valhalla-uk-"+tag.removeprefix("routing-uk-")+".tar.gz.part"
    for p in parts:
        check(len(p)<=151 and re.fullmatch(re.escape(prefix)+r"\d{2}",p) is not None,
              "INVALID_PART_NAME")
    check(len(set(parts))==len(parts),"DUPLICATE_PART_NAME")
    expected=[prefix+str(i).zfill(2) for i in range(len(parts))]
    check(parts==expected,"INVALID_PART_ORDER")
    metadata=doc.get("part_metadata")
    check(isinstance(metadata,dict) and set(metadata)==set(parts),
          "PART_METADATA_REQUIRED")
    size_sum=0
    for p in parts:
        row=metadata[p]
        check(isinstance(row,dict) and row.get("tag")==tag and
              type(row.get("bytes")) is int and 0<row["bytes"]<=MAX_CHUNK
              and isinstance(row.get("sha256"),str)
              and HEX64.fullmatch(row["sha256"]),"PART_DESCRIPTOR_INVALID")
        size_sum+=row["bytes"]
    check(size_sum==doc["download_bytes"],"COMPRESSED_SIZE_MISMATCH")
    return doc


def release_assets(release,tag,code):
    check(isinstance(release,dict) and release.get("tag_name")==tag,code)
    assets=release.get("assets")
    check(isinstance(assets,list), "RELEASE_ASSET_SET_INVALID")
    names={}
    for entry in assets:
        check(isinstance(entry,dict) and isinstance(entry.get("name"),str),
              "RELEASE_ASSET_SET_INVALID")
        name=entry["name"]
        check(name not in names,"RELEASE_ASSET_DUPLICATE")
        names[name]=entry
    return names


def matched_asset(assets,name,length,digest,code):
    item=assets.get(name)
    check(isinstance(item,dict) and type(item.get("size")) is int and
          item["size"]==length and item.get("digest")=="sha256:"+digest,code)


def inspect_release(pointer,rolling,dated,app_repository,publisher_repository):
    # A release in the wrong repository cannot substitute for the known producer.
    check(publisher_repository==OWNER,"RELEASE_OWNER_MISMATCH")
    doc=load_manifest(pointer)
    tag=doc["generation"]
    rolling_assets=release_assets(rolling,ROLLING_TAG,"ROLLING_RELEASE_TAG_MISMATCH")
    dated_assets=release_assets(dated,tag,"DATED_RELEASE_TAG_MISMATCH")
    matched_asset(rolling_assets,"routing-uk.json",len(pointer),hashlib.sha256(pointer).hexdigest(),
                  "POINTER_ASSET_MISSING_OR_DUPLICATED")
    matched_asset(dated_assets,"routing-uk.json",len(pointer),hashlib.sha256(pointer).hexdigest(),
                  "POINTER_ASSET_MISSING_OR_DUPLICATED")
    parts=doc["parts"]
    for name in parts:
        meta=doc["part_metadata"][name]
        matched_asset(rolling_assets,name,meta["bytes"],meta["sha256"],
                      "CHUNK_RELEASE_MISMATCH")
        matched_asset(dated_assets,name,meta["bytes"],meta["sha256"],
                      "CHUNK_RELEASE_MISMATCH")
    check(set(dated_assets)==set(parts)|{"routing-uk.json"},
          "DATED_RELEASE_ASSET_SET_MISMATCH")
    matched=app_repository==publisher_repository
    return {
        "schema":1,"scope":"release_metadata_and_manifest_only",
        "status":"release_metadata_verified_only" if matched else
                 "blocked_consumer_repository_mismatch",
        "publisher_repository":publisher_repository,
        "app_repository":app_repository,
        "generation":tag,
        "graph_sha256":doc["sha256"],
        "manifest_sha256":hashlib.sha256(pointer).hexdigest(),
        "parts_metadata_verified":len(parts),
        "compressed_bytes_metadata":doc["download_bytes"],
        "app_consumer_compatible":matched,
        "graph_bytes_verified":False,
        "legal_road_truth":"unknown",
        "geographic_coverage":"unknown",
        "offline_navigation_accepted":False,
    }


def verify_chunk_files(pointer,directory):
    """Streaming SHA-256 checks of authentic *local* chunks + decoded graph.

    Requires a prevalidated manifest and exact files. Not an Android install
    test and not legal/UK coverage verification. Streaming bounded per read.
    """
    doc=load_manifest(pointer)
    directory=Path(directory)
    check(directory.is_dir() and not directory.is_symlink(),
          "INVALID_CHUNK_DIRECTORY")
    digest=hashlib.sha256()
    total=0
    compressed=0
    decoder=zlib.decompressobj(wbits=16+zlib.MAX_WBITS)
    for name in doc["parts"]:
        path=directory/name
        check(path.is_file() and not path.is_symlink(),"MISSING_CHUNK")
        row=doc["part_metadata"][name]
        check(path.stat().st_size==row["bytes"],"CHUNK_INTEGRITY_MISMATCH")
        part_sha=hashlib.sha256()
        with path.open("rb") as handle:
            for block in iter(lambda:handle.read(1024*1024),b""):
                part_sha.update(block)
                compressed+=len(block)
                pending=block
                while pending:
                    output=decoder.decompress(pending, min(1024*1024,doc["bytes"]-total+1))
                    pending=decoder.unconsumed_tail
                    total+=len(output)
                    check(total<=doc["bytes"],"GRAPH_INTEGRITY_MISMATCH")
                    digest.update(output)
                    check(not decoder.unused_data,"GRAPH_TRAILING_GZIP_DATA")
        check(part_sha.hexdigest()==row["sha256"],"CHUNK_INTEGRITY_MISMATCH")
    check(compressed==doc["download_bytes"],"COMPRESSED_SIZE_MISMATCH")
    check(decoder.eof,"GRAPH_GZIP_INCOMPLETE")
    check(total==doc["bytes"] and digest.hexdigest()==doc["sha256"],
          "GRAPH_INTEGRITY_MISMATCH")
    return {"schema":1,"status":"local_chunk_bytes_verified_only",
            "scope":"streaming_graph_integrity_without_android_or_navigation",
            "verified_chunks":len(doc["parts"]),"graph_sha256":digest.hexdigest(),
            "graph_bytes_verified":True,
            "legal_road_truth":"unknown","geographic_coverage":"unknown",
            "offline_navigation_accepted":False}
