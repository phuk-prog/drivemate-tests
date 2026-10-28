"""TF-06 DMN-first frozen consumer and local byte-readback checks.

Artificial bytes test the verifier only; they are NOT authentic road coverage,
navigation routes, independent legal road truth or an installed phone receipt.
"""
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest

from dmn_release_verifier import (
    app_consumer_contract,manifest_pieces,release_contract,verify_local_pieces)

SHA=lambda x:hashlib.sha256(x).hexdigest()
ROOT=Path(os.environ.get("DRIVEMATE_APP_ROOT","app-src"))

DMN_SOURCE='''object DmnTiles {
 private val RELEASES: String get() = MapReleaseSource.base
 private val BASE: String get() = "$RELEASES/dmn-uk"
 private const val INDEX = "dmn-uk.json"
 fun fetchAll() {}
 fun router() { val idx = index(dir); DmnPack.merge(1) { i -> x }; val r = DmnRouter(pack) }
 val receipt = piece.sha256
}'''
MAP_SOURCE='val base = BuildConfig.MAP_RELEASES_BASE_URL'
GRADLE='val url = "https://github.com/phuk-prog/drivemate-map/releases/download"'

def a(name,data):
    return {"name":name,"size":len(data),"digest":"sha256:"+SHA(data)}

def data():
    items={
        "dmn-212_-9.dmnpack":gzip.compress(b"DMNPACK2 fixture tile",mtime=0),
        "dmn-main-27_-2.dmnpack":gzip.compress(b"DMNPACK2 fixture corridor",mtime=0),
        "dmn-main-uk.dmnpack":gzip.compress(b"DMNPACK2 fixture trunk",mtime=0)}
    p=lambda n:{"file":n,"bytes":len(items[n]),"sha256":SHA(items[n])}
    doc={"schema":1,"format":"DMNPACK2","release":"dmn-uk-20261028-15",
         "tiles":{"212_-9":p("dmn-212_-9.dmnpack")},
         "regions":{"main-27_-2":p("dmn-main-27_-2.dmnpack")},
         "main":p("dmn-main-uk.dmnpack")}
    raw=json.dumps(doc).encode()
    rolling={"tag_name":"dmn-uk","assets":[a("dmn-uk.json",raw)]}
    dated={"tag_name":doc["release"],"assets":[a(n,b) for n,b in items.items()]}
    return raw,rolling,dated,items

class PrimaryDmnReleaseContract(unittest.TestCase):
    def setUp(self):
        self.raw,self.rolling,self.dated,self.files=data()
        self.wired=app_consumer_contract(DMN_SOURCE,MAP_SOURCE,GRADLE)

    def test_dmn_is_primary_engine_and_valhalla_not_acceptance(self):
        r=release_contract(self.raw,self.rolling,self.dated,self.wired)
        self.assertEqual("DriveMate Navigation (DMN)",r["engine"])
        self.assertEqual(3,r["pieces_verified_metadata"])
        self.assertTrue(r["source_consumer_wiring_verified"])
        self.assertFalse(r["offline_navigation_accepted"])
        self.assertFalse(r["downloaded_bytes_verified"])
        self.assertEqual("unknown",r["uk_road_completeness"])
        self.assertEqual("unknown_not_established_by_index",r["legal_rights_status"])

    def test_actual_app_files_link_dmn_tiles_to_own_router_and_publisher(self):
        tiles=(ROOT/"app/src/main/java/com/drivemate/app/dmn/DmnTiles.kt").read_text()
        source=(ROOT/"app/src/main/java/com/drivemate/app/data/MapReleaseSource.kt").read_text()
        gradle=(ROOT/"app/build.gradle.kts").read_text()
        r=app_consumer_contract(tiles,source,gradle)
        self.assertEqual("phuk-prog/drivemate-map",r["release_repository_default"].split("/")[0]+"/"+r["release_repository_default"].split("/")[1])
        self.assertFalse(r["routing_functional_verified"])
        self.assertFalse(r["compiled_override_or_device_endpoint_verified"])

    def test_wrong_default_publisher_fails_closed(self):
        with self.assertRaisesRegex(ValueError,"DMN_DEFAULT_PUBLISHER_MISMATCH"):
            app_consumer_contract(DMN_SOURCE,MAP_SOURCE,GRADLE.replace("drivemate-map","drivemate-data"))

    def test_missing_dmn_router_cannot_be_replaced_by_valhalla(self):
        with self.assertRaisesRegex(ValueError,"DMN_ROUTE_CONSUMER_UNVERIFIED"):
            app_consumer_contract(DMN_SOURCE.replace("DmnRouter(","ValhallaRouter("),MAP_SOURCE,GRADLE)

    def test_missing_dmn_download_index_blocks_wiring(self):
        with self.assertRaisesRegex(ValueError,"DMN_APP_SOURCE_NOT_ALIGNED"):
            app_consumer_contract(DMN_SOURCE.replace("dmn-uk.json","routing-uk.json"),MAP_SOURCE,GRADLE)

    def test_dmn_release_metadata_without_consumer_does_not_link_app(self):
        r=release_contract(self.raw,self.rolling,self.dated)
        self.assertFalse(r["source_consumer_wiring_verified"])

    def test_missing_or_malicious_dmn_piece_fails_closed(self):
        broken=json.loads(self.raw)
        broken["tiles"]["212_-9"]["file"]="../outside.dmnpack"
        with self.assertRaisesRegex(ValueError,"DMN_PART_NAME_INVALID"):
            manifest_pieces(json.dumps(broken).encode())

    def test_duplicate_dmn_piece_name_fails_closed(self):
        broken=json.loads(self.raw)
        broken["regions"]["main-27_-2"]["file"]=broken["tiles"]["212_-9"]["file"]
        with self.assertRaisesRegex(ValueError,"DMN_DUPLICATE_PIECE"):
            manifest_pieces(json.dumps(broken).encode())

    def test_dmn_release_wrong_piece_hash_blocks(self):
        self.dated["assets"][0]["digest"]="sha256:"+"f"*64
        with self.assertRaises(ValueError):
            release_contract(self.raw,self.rolling,self.dated,self.wired)

    def test_dmn_installed_bytes_are_verified_by_sha_not_checkmark(self):
        with tempfile.TemporaryDirectory() as td:
            for name,blob in self.files.items():
                (Path(td)/name).write_bytes(blob)
            result=verify_local_pieces(self.raw,td)
        self.assertEqual(3,result["pieces_verified"])
        self.assertTrue(result["actual_local_bytes_verified"])
        self.assertFalse(result["android_production_parser_verified"])
        self.assertFalse(result["offline_route_verified"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_missing_and_corrupted_dmn_piece_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            for name,blob in self.files.items():
                (Path(td)/name).write_bytes(blob)
            (Path(td)/"dmn-main-uk.dmnpack").unlink()
            with self.assertRaisesRegex(ValueError,"DMN_PART_MISSING"):
                verify_local_pieces(self.raw,td)
            (Path(td)/"dmn-main-uk.dmnpack").write_bytes(b"corrupt")
            with self.assertRaisesRegex(ValueError,"DMN_PART_BYTES_MISMATCH"):
                verify_local_pieces(self.raw,td)

    def test_wrong_dmn_format_is_not_accepted_as_production_pack(self):
        with tempfile.TemporaryDirectory() as td:
            files=dict(self.files)
            name="dmn-main-uk.dmnpack"
            files[name]=gzip.compress(b"DMNPACK1 older pack",mtime=0)
            doc=json.loads(self.raw)
            doc["main"]["bytes"]=len(files[name])
            doc["main"]["sha256"]=SHA(files[name])
            for path,blob in files.items():
                (Path(td)/path).write_bytes(blob)
            with self.assertRaisesRegex(ValueError,"DMN_PART_FORMAT_INCOMPATIBLE"):
                verify_local_pieces(json.dumps(doc).encode(),td)

if __name__=="__main__":
    unittest.main()
