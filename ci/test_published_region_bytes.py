"""Frozen local-byte download verification regressions; no synthetic UK roads.

Stubbed test payloads are *release file fixtures*, never evidence of real map
bytes. The live CI job must separately download and SHA-256 hash real releases.
"""
import hashlib
import importlib
import json
from pathlib import Path
import tempfile
import unittest


class PublishedFileByteTests(unittest.TestCase):
    def setUp(self):
        try:
            self.mod=importlib.import_module("download_published_region_files")
        except ModuleNotFoundError:
            self.fail("PUBLISHED_BYTES_DOWNLOADER_MISSING")
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.mapbytes=b"real asset byte hashing test fixture, not a road"
        self.mapref={"tag":"map-data-region-wales-r1-a1",
                     "bytes":len(self.mapbytes),
                     "sha256":hashlib.sha256(self.mapbytes).hexdigest()}
        self.manifest={"schema":1,"coverage":"partial","region":{"id":"wales"},
                       "files":{"drivemate-v1.pmtiles":dict(self.mapref)}}

    def test_streamed_local_bytes_match_pinned_sha_and_size(self):
        p=self.root/"drivemate-v1.pmtiles"
        p.write_bytes(self.mapbytes)
        result=self.mod.verify_download(p,self.mapref)
        self.assertEqual(self.mapref["sha256"],result["sha256"])
        self.assertEqual(self.mapref["bytes"],result["bytes"])

    def test_corrupted_bytes_rejected_even_if_same_length(self):
        p=self.root/"a";p.write_bytes(b"x"*len(self.mapbytes))
        with self.assertRaisesRegex(ValueError,"PUBLISHED_FILE_HASH_MISMATCH"):
            self.mod.verify_download(p,self.mapref)

    def test_truncated_bytes_rejected(self):
        p=self.root/"a";p.write_bytes(self.mapbytes[:-1])
        with self.assertRaisesRegex(ValueError,"PUBLISHED_FILE_SIZE_MISMATCH"):
            self.mod.verify_download(p,self.mapref)

    def test_manifest_crosschecks_actual_map_pointer(self):
        result=self.mod.validate_manifest(json.dumps(self.manifest).encode(),"wales",self.mapref)
        self.assertEqual(1,result["schema"])
        self.assertEqual("wales",result["region"])
        self.assertGreaterEqual(result["listed_files"],1)

    def test_manifest_wrong_map_digest_fails_closed(self):
        self.manifest["files"]["drivemate-v1.pmtiles"]["sha256"]="0"*64
        with self.assertRaisesRegex(ValueError,"MANIFEST_MAP_POINTER_MISMATCH"):
            self.mod.validate_manifest(json.dumps(self.manifest).encode(),"wales",self.mapref)

    def test_manifest_wrong_region_fails_closed(self):
        self.manifest["region"]["id"]="scotland"
        with self.assertRaisesRegex(ValueError,"MANIFEST_REGION_MISMATCH"):
            self.mod.validate_manifest(json.dumps(self.manifest).encode(),"wales",self.mapref)

    def test_missing_published_asset_is_not_mistaken_for_verified(self):
        self.assertTrue(callable(getattr(self.mod,"run_batch",None)))
        manifestbytes=json.dumps(self.manifest).encode()
        c={"schema":1,"coverage":"partial","regions":{"wales":{
             "id":"wales","manifest":{"tag":"map-data-region-wales-r1-a1",
                          "bytes":len(manifestbytes),"sha256":hashlib.sha256(manifestbytes).hexdigest()},
             "map":dict(self.mapref)}}}
        def fake_fetch(tag,name,destination):
            if name=="manifest.json":
                destination.write_bytes(manifestbytes)
            else:
                raise OSError("real download missing")
        out=self.mod.run_batch(c,index=0,count=1,fetch=fake_fetch)
        self.assertEqual(1,out["failed"])
        self.assertEqual(0,out["verified"])
        self.assertEqual(1,out["total"])

    def test_batch_selection_covers_each_region_once_without_double_counting(self):
        self.assertTrue(callable(getattr(self.mod,"region_batch",None)))
        ids=["bedfordshire","greater-manchester","scotland","wales","northern-ireland-area"]
        groups=[self.mod.region_batch(ids,index=i,count=3) for i in range(3)]
        self.assertEqual(sorted(ids),sorted(x for group in groups for x in group))
        self.assertEqual(len(ids),len(set(x for group in groups for x in group)))


if __name__=="__main__":
    unittest.main()
