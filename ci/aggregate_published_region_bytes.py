"""Aggregate four pinned actual-release download batches without false greens."""
import argparse
import hashlib
import json
from pathlib import Path


def aggregate(catalog_bytes, expected_sha, batches, expected_count=4):
    if hashlib.sha256(catalog_bytes).hexdigest()!=expected_sha:
        raise ValueError("CATALOG_SNAPSHOT_MISMATCH")
    catalog=json.loads(catalog_bytes)
    names=set(catalog["regions"])
    seen=set()
    if len(batches)!=expected_count:
        raise ValueError("BATCH_RECEIPT_MISSING")
    indexes=set()
    good=0
    bad=0
    manifest_bytes=0
    map_bytes=0
    for batch in batches:
        idx=batch.get("batch_index")
        if (batch.get("schema")!=1 or batch.get("scope")!="actual_published_map_and_manifest_bytes_only"
            or batch.get("catalog_sha256")!=expected_sha
            or batch.get("batch_count")!=expected_count
            or type(idx) is not int or idx in indexes or not 0<=idx<expected_count
            or batch.get("offline_navigation_accepted") is not False
            or batch.get("geographic_coverage_verified") is not False):
            raise ValueError("INVALID_BATCH_RECEIPT")
        indexes.add(idx)
        rows=batch.get("regions")
        if not isinstance(rows,list):
            raise ValueError("INVALID_BATCH_RECEIPT")
        expected=[n for i,n in enumerate(sorted(names)) if i%expected_count==idx]
        if sorted(x.get("region","") for x in rows)!=sorted(expected):
            raise ValueError("UNMATCHED_REGION_BATCH")
        for row in rows:
            name=row["region"]
            if name in seen or name not in names:
                raise ValueError("DUPLICATE_OR_UNKNOWN_REGION")
            seen.add(name)
            if row.get("status")!="passed":
                bad+=1
            else:
                manifest=names and catalog["regions"][name]["manifest"]
                mapref=catalog["regions"][name]["map"]
                if (row.get("manifest_bytes_verified")!=manifest["bytes"]
                    or row.get("map_bytes_verified")!=mapref["bytes"]
                    or row.get("manifest_sha256")!=manifest["sha256"]
                    or row.get("map_sha256")!=mapref["sha256"]):
                    raise ValueError("BATCH_CONTENT_MISMATCH")
                manifest_bytes+=manifest["bytes"]
                map_bytes+=mapref["bytes"]
                good+=1
        if batch.get("total")!=len(rows) or batch.get("verified")!=sum(r["status"]=="passed" for r in rows):
            raise ValueError("BATCH_COUNT_INCONSISTENT")
    if seen!=names or len(indexes)!=expected_count:
        raise ValueError("CATALOG_REGION_COVERAGE_MISSING")
    return {"schema":1,"catalog_sha256":expected_sha,
            "scope":"actual_published_map_and_manifest_bytes_only",
            "total_regions":len(names),"verified_regions":good,"failed_regions":bad,
            "manifest_bytes_verified":manifest_bytes,"map_bytes_verified":map_bytes,
            "offline_navigation_accepted":False,"uk_geographic_coverage_verified":False,
            "status":"passed" if good==len(names) and bad==0 else "failed"}


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--catalog",required=True)
    p.add_argument("--sha256",required=True)
    p.add_argument("--receipts",required=True)
    p.add_argument("--output",required=True)
    a=p.parse_args()
    files=sorted(Path(a.receipts).rglob("batch-*.json"))
    batch=[json.loads(x.read_text()) for x in files]
    result=aggregate(Path(a.catalog).read_bytes(),a.sha256,batch)
    output=Path(a.output)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    print(json.dumps(result,sort_keys=True))
    if result["status"]!="passed":
        raise SystemExit(1)


if __name__=="__main__":
    main()
