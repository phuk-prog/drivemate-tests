"""Stream and SHA-256 verify *downloaded* public regional manifest and map bytes.

Public testing process only. Actual PMTiles bytes are read and discarded after
hashing; only bounded per-region verification receipts are retained. A validated
map download is NOT UK-wide coverage, source licensing, production app cache,
offline routing or navigation-screen acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

HEX64 = re.compile(r"[0-9a-f]{64}\Z")
REGION = re.compile(r"[a-z0-9][a-z0-9-]{0,70}\Z")
TAG = re.compile(r"map-data-region-[a-z0-9-]+-r[0-9]+-a[0-9]+(?:-part[0-9]{3})?\Z")
MAX_ASSET_BYTES = 1_950_000_000
MAX_MANIFEST_BYTES = 2_000_000


def verify_download(path, pointer):
    """Measure bytes *locally*, in bounded chunks, against pinned catalogue."""
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError("PUBLISHED_FILE_MISSING_OR_LINK")
    expected_bytes=pointer.get("bytes") if isinstance(pointer,dict) else None
    digest=pointer.get("sha256") if isinstance(pointer,dict) else None
    if (type(expected_bytes) is not int or not 0 < expected_bytes <= MAX_ASSET_BYTES
            or not isinstance(digest,str) or not HEX64.fullmatch(digest)):
        raise ValueError("INVALID_PUBLISHED_FILE_POINTER")
    if path.stat().st_size != expected_bytes:
        raise ValueError("PUBLISHED_FILE_SIZE_MISMATCH")
    hasher=hashlib.sha256()
    size=0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            size+=len(chunk)
            hasher.update(chunk)
    if size != expected_bytes:
        raise ValueError("PUBLISHED_FILE_SIZE_MISMATCH")
    actual=hasher.hexdigest()
    if actual != digest:
        raise ValueError("PUBLISHED_FILE_HASH_MISMATCH")
    return {"bytes":size,"sha256":actual}


def validate_manifest(raw, region_name, map_pointer):
    if not isinstance(raw,bytes) or not 0 < len(raw) <= MAX_MANIFEST_BYTES:
        raise ValueError("INVALID_PUBLISHED_MANIFEST_SIZE")
    try:
        value=json.loads(raw)
    except (ValueError,UnicodeError):
        raise ValueError("INVALID_PUBLISHED_MANIFEST_JSON") from None
    if not isinstance(value,dict) or value.get("schema")!=1 or value.get("coverage")!="partial":
        raise ValueError("INVALID_PUBLISHED_MANIFEST_SCHEMA")
    region=value.get("region")
    if not isinstance(region,dict) or region.get("id")!=region_name:
        raise ValueError("MANIFEST_REGION_MISMATCH")
    files=value.get("files")
    if not isinstance(files,dict) or not files:
        raise ValueError("MANIFEST_FILES_MISSING")
    for name, item in files.items():
        if (not isinstance(name,str) or not name or len(name)>220 or "/" in name
            or "\\" in name or ".." in name or not isinstance(item,dict)
            or type(item.get("bytes")) is not int or not 0 < item["bytes"] <= MAX_ASSET_BYTES
            or not isinstance(item.get("sha256"),str) or not HEX64.fullmatch(item["sha256"])
            or not isinstance(item.get("tag"),str) or not TAG.fullmatch(item["tag"])
            or not item["tag"].startswith("map-data-region-" + region_name + "-")):
            raise ValueError("MANIFEST_FILE_POINTER_INVALID")
    item=files.get("drivemate-v1.pmtiles")
    if (not isinstance(item,dict) or
        any(item.get(k)!=map_pointer.get(k) for k in ("tag","bytes","sha256"))):
        raise ValueError("MANIFEST_MAP_POINTER_MISMATCH")
    return {"schema":1,"region":region_name,"listed_files":len(files),
            "map_pointer_matched":True}


def region_batch(ids, index, count):
    if type(index) is not int or type(count) is not int or count<1 or not 0<=index<count:
        raise ValueError("INVALID_BATCH")
    return [name for n,name in enumerate(sorted(ids)) if n % count==index]


def download_asset(tag, name, destination):
    if not isinstance(tag,str) or not TAG.fullmatch(tag):
        raise ValueError("INVALID_ASSET_TAG")
    if name not in ("manifest.json","drivemate-v1.pmtiles"):
        raise ValueError("INVALID_ASSET_NAME")
    destination=Path(destination)
    # gh download writes filename, never stores code or a private app checkout.
    # Each region uses a bounded temporary directory immediately cleared on exit.
    cmd=["gh","release","download",tag,
         "--repo","phuk-prog/drivemate-map",
         "--pattern",name,"--dir",str(destination.parent)]
    for attempt in range(2):
        p=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                         stderr=subprocess.PIPE,timeout=1200,check=False)
        if p.returncode==0 and destination.is_file():
            return
        if destination.exists():
            destination.unlink()
    raise OSError("PUBLISHED_ASSET_DOWNLOAD_FAILED")


def run_batch(catalog,index,count,fetch=download_asset,progress=None):
    regions=catalog.get("regions") if isinstance(catalog,dict) else None
    if (not isinstance(regions,dict) or not regions or catalog.get("schema")!=1
        or catalog.get("coverage")!="partial"):
        raise ValueError("INVALID_PUBLISHED_CATALOG")
    ids=region_batch(list(regions),index,count)
    results=[]
    for name in ids:
        row={"region":name,"status":"failed","reason":"NOT_CHECKED",
             "manifest_bytes_verified":0,"map_bytes_verified":0}
        try:
            if not isinstance(name,str) or not REGION.fullmatch(name):
                raise ValueError("INVALID_REGION_ID")
            region=regions[name]
            if not isinstance(region,dict) or region.get("id")!=name:
                raise ValueError("INVALID_REGION_ENTRY")
            manifest_ref=region.get("manifest")
            map_ref=region.get("map")
            if not isinstance(manifest_ref,dict) or not isinstance(map_ref,dict):
                raise ValueError("REGION_FILE_POINTER_MISSING")
            with tempfile.TemporaryDirectory(prefix="dmn-published-") as tmp:
                parent=Path(tmp)
                manifest=parent/"manifest.json"
                fetch(manifest_ref["tag"],"manifest.json",manifest)
                m=verify_download(manifest,manifest_ref)
                manifest_summary=validate_manifest(manifest.read_bytes(),name,map_ref)
                row["manifest_bytes_verified"]=m["bytes"]
                row["manifest_files_listed"]=manifest_summary["listed_files"]
                # Keep largest download to one file on disk, then discard.
                manifest.unlink()
                map_file=parent/"drivemate-v1.pmtiles"
                fetch(map_ref["tag"],"drivemate-v1.pmtiles",map_file)
                mapped=verify_download(map_file,map_ref)
                row["map_bytes_verified"]=mapped["bytes"]
                row["map_sha256"]=mapped["sha256"]
                row["manifest_sha256"]=m["sha256"]
                row["status"]="passed"
                row["reason"]=None
        except (ValueError,KeyError,OSError,subprocess.TimeoutExpired) as e:
            row["status"]="failed"
            # Restrict messages to stable codes, never emit raw URL/path/token.
            row["reason"]=str(e) if isinstance(e,ValueError) and str(e).isupper() and len(str(e))<100 else "PUBLISHED_DOWNLOAD_OR_IO_FAILURE"
        results.append(row)
        if progress is not None:
            progress(results)
    return {"schema":1,"scope":"actual_published_map_and_manifest_bytes_only",
            "batch_index":index,"batch_count":count,
            "total":len(results),"verified":sum(r["status"]=="passed" for r in results),
            "failed":sum(r["status"]!="passed" for r in results),
            "manifest_bytes_verified":sum(r["manifest_bytes_verified"] for r in results if r["status"]=="passed"),
            "map_bytes_verified":sum(r["map_bytes_verified"] for r in results if r["status"]=="passed"),
            "offline_navigation_accepted":False,
            "geographic_coverage_verified":False,"regions":results}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog",required=True)
    parser.add_argument("--catalog-sha256",required=True)
    parser.add_argument("--batch-index",type=int,required=True)
    parser.add_argument("--batch-count",type=int,required=True)
    parser.add_argument("--output",required=True)
    a=parser.parse_args()
    raw=Path(a.catalog).read_bytes()
    if not HEX64.fullmatch(a.catalog_sha256) or hashlib.sha256(raw).hexdigest()!=a.catalog_sha256:
        raise SystemExit("CATALOG_SNAPSHOT_DIGEST_MISMATCH")
    catalog=json.loads(raw)
    out=Path(a.output)
    out.parent.mkdir(parents=True,exist_ok=True)
    def record(rows):
        # Partial evidence persists after every region for CI diagnostics.
        out.write_text(json.dumps({"schema":1,"partial":True,"regions":rows},indent=2)+"\n")
    result=run_batch(catalog,a.batch_index,a.batch_count,progress=record)
    result["catalog_sha256"]=a.catalog_sha256
    out.write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    print(json.dumps({k:result[k] for k in (
        "batch_index","total","verified","failed","manifest_bytes_verified",
        "map_bytes_verified","catalog_sha256")},sort_keys=True),flush=True)
    if result["failed"] or not result["total"]:
        raise SystemExit(1)


if __name__=="__main__":
    main()
