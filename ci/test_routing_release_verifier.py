"""TF-06 frozen release-contract regressions. Fixtures are bytes, not UK roads."""
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from routing_release_verifier import inspect_release, verify_chunk_files

SHA=lambda raw:hashlib.sha256(raw).hexdigest()
TAG="routing-uk-20261028-r40747988880-a1"
NAMES=["valhalla-uk-20261028-r40747988880-a1.tar.gz.part00",
       "valhalla-uk-20261028-r40747988880-a1.tar.gz.part01"]


def fixture():
    graph=b"not a real graph: TF-06 bytes-only fixture" * 4
    compressed=gzip.compress(graph,mtime=0)
    cut=len(compressed)//2
    blobs=dict(zip(NAMES,[compressed[:cut],compressed[cut:]]))
    manifest={
        "engine":"valhalla-3.6.3","sha256":SHA(graph),"bytes":len(graph),
        "download_bytes":len(compressed),"built":"2026-10-28T12:00:00Z",
        "generation":TAG,"parts":NAMES,
        "source":{"sha256":"a"*64,
                  "url":"https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf",
                  "retrieved_at":"2026-10-28T06:00:00Z",
                  "input_scope":"UK"},
        "build_commit":"b"*40,"build_run":"40747988880",
        "coverage_validation":"Six smoke routes, not full UK coverage",
        "part_metadata":{name:{"tag":TAG,"bytes":len(raw),"sha256":SHA(raw)}
                         for name,raw in blobs.items()}
    }
    raw=json.dumps(manifest,sort_keys=True).encode()
    assets=[{"name":name,"size":len(blob),"digest":"sha256:"+SHA(blob)}
            for name,blob in blobs.items()]
    assets.append({"name":"routing-uk.json","size":len(raw),"digest":"sha256:"+SHA(raw)})
    dated={"tag_name":TAG,"assets":list(assets)}
    rolling={"tag_name":"routing-uk","assets":list(assets)}
    return raw,rolling,dated,blobs


class GraphReleaseVerification(unittest.TestCase):
    def setUp(self):
        self.raw,self.rolling,self.dated,self.chunks=fixture()

    def checked(self,**overrides):
        params=dict(pointer=self.raw,rolling=self.rolling,dated=self.dated,
                    app_repository="phuk-prog/drivemate-map",
                    publisher_repository="phuk-prog/drivemate-map")
        params.update(overrides)
        return inspect_release(**params)

    def edit(self,modify):
        doc=json.loads(self.raw)
        modify(doc)
        self.raw=json.dumps(doc,sort_keys=True).encode()
        # Keep both pointer metadata aligned, isolate semantic errors.
        for release in (self.rolling,self.dated):
            for asset in release["assets"]:
                if asset["name"]=="routing-uk.json":
                    asset["size"]=len(self.raw)
                    asset["digest"]="sha256:"+SHA(self.raw)

    def test_metadata_passes_but_is_not_graph_or_offline_acceptance(self):
        r=self.checked()
        self.assertEqual(2,r["parts_metadata_verified"])
        self.assertEqual("release_metadata_verified_only",r["status"])
        self.assertFalse(r["graph_bytes_verified"])
        self.assertFalse(r["offline_navigation_accepted"])
        self.assertEqual("unknown",r["legal_road_truth"])

    def test_shipped_app_repository_mismatch_never_passes(self):
        r=self.checked(app_repository="phuk-prog/drivemate-data")
        self.assertEqual("blocked_consumer_repository_mismatch",r["status"])
        self.assertFalse(r["app_consumer_compatible"])

    def test_wrong_release_owner_is_not_accepted(self):
        with self.assertRaisesRegex(ValueError,"RELEASE_OWNER_MISMATCH"):
            self.checked(publisher_repository="other/unreviewed")

    def test_rolling_and_dated_release_tags_are_pinned(self):
        self.rolling["tag_name"]="routing-uk-latest"
        with self.assertRaisesRegex(ValueError,"ROLLING_RELEASE_TAG_MISMATCH"):
            self.checked()
        self.rolling["tag_name"]="routing-uk"
        self.dated["tag_name"]=TAG+"-other"
        with self.assertRaisesRegex(ValueError,"DATED_RELEASE_TAG_MISMATCH"):
            self.checked()

    def test_missing_or_duplicate_pointer_fails(self):
        self.rolling["assets"]=[a for a in self.rolling["assets"] if a["name"]!="routing-uk.json"]
        with self.assertRaisesRegex(ValueError,"POINTER_ASSET_MISSING_OR_DUPLICATED"):
            self.checked()

    def test_release_asset_digest_and_size_are_required(self):
        self.dated["assets"][0]["digest"]="sha256:"+"f"*64
        with self.assertRaisesRegex(ValueError,"CHUNK_RELEASE_MISMATCH"):
            self.checked()

    def test_immutable_release_must_not_have_unexpected_asset(self):
        self.dated["assets"].append({"name":"surprise.bin","size":1,"digest":"sha256:"+"a"*64})
        with self.assertRaisesRegex(ValueError,"DATED_RELEASE_ASSET_SET_MISMATCH"):
            self.checked()

    def test_missing_part_metadata_fails_closed(self):
        self.edit(lambda d:d.pop("part_metadata"))
        with self.assertRaisesRegex(ValueError,"PART_METADATA_REQUIRED"):
            self.checked()

    def test_compressed_total_and_manifest_bytes_cannot_drift(self):
        self.edit(lambda d:d.__setitem__("download_bytes",d["download_bytes"]+1))
        with self.assertRaisesRegex(ValueError,"COMPRESSED_SIZE_MISMATCH"):
            self.checked()

    def test_wrong_generation_or_build_run_fails(self):
        self.edit(lambda d:d.__setitem__("build_run","1234"))
        with self.assertRaisesRegex(ValueError,"BUILD_IDENTITY_MISMATCH"):
            self.checked()

    def test_untrusted_part_path_or_wrong_order_fails(self):
        self.edit(lambda d:d["parts"].reverse())
        with self.assertRaisesRegex(ValueError,"INVALID_PART_ORDER"):
            self.checked()
        self.raw,self.rolling,self.dated,self.chunks=fixture()
        self.edit(lambda d:d["parts"].__setitem__(0,"../outside.tar.gz.part00"))
        with self.assertRaisesRegex(ValueError,"INVALID_PART_NAME"):
            self.checked()

    def test_invalid_engine_and_missing_provenance_block(self):
        self.edit(lambda d:d.__setitem__("engine","osrm-unknown"))
        with self.assertRaisesRegex(ValueError,"GRAPH_ENGINE_INCOMPATIBLE"):
            self.checked()
        self.raw,self.rolling,self.dated,self.chunks=fixture()
        self.edit(lambda d:d["source"].__setitem__("sha256","not-a-hash"))
        with self.assertRaisesRegex(ValueError,"SOURCE_PROVENANCE_INCOMPLETE"):
            self.checked()

    def test_source_date_and_immutable_source_hash_are_required(self):
        self.edit(lambda d:d["source"].__setitem__("retrieved_at","not a date"))
        with self.assertRaisesRegex(ValueError,"SOURCE_PROVENANCE_INCOMPLETE"):
            self.checked()

    def test_verified_complete_chunks_decode_to_the_graph_hash(self):
        self.checked()
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            for name,data in self.chunks.items():
                (p/name).write_bytes(data)
            r=verify_chunk_files(self.raw,p)
        self.assertEqual(2,r["verified_chunks"])
        self.assertTrue(r["graph_bytes_verified"])
        self.assertFalse(r["offline_navigation_accepted"])

    def test_missing_corrupt_and_truncated_parts_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            for name,data in self.chunks.items():
                (p/name).write_bytes(data)
            (p/NAMES[1]).unlink()
            with self.assertRaisesRegex(ValueError,"MISSING_CHUNK"):
                verify_chunk_files(self.raw,p)
            (p/NAMES[1]).write_bytes(self.chunks[NAMES[1]][:-1])
            with self.assertRaisesRegex(ValueError,"CHUNK_INTEGRITY_MISMATCH"):
                verify_chunk_files(self.raw,p)

    def test_valid_chunk_hashes_do_not_mask_wrong_final_graph_digest(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            for name,data in self.chunks.items():
                (p/name).write_bytes(data)
            self.edit(lambda d:d.__setitem__("sha256","f"*64))
            with self.assertRaisesRegex(ValueError,"GRAPH_INTEGRITY_MISMATCH"):
                verify_chunk_files(self.raw,p)


if __name__=="__main__":
    unittest.main()
