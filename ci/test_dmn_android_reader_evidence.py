"""Evidence must fail closed on missing, mismatched or overstated DMN parser receipts."""
import gzip
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from dmn_android_reader_evidence import verify, verify_install_route, verify_four_nation_routes


class TestAndroidDmnReaderEvidence(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        d=Path(self.tmp.name)
        self.index=d/"index.json"
        self.piece=d/"piece.dmnpack"
        self.receipt=d/"receipt.json"
        raw=b"fixture-not-real-map-pack"
        self.piece.write_bytes(raw)
        sha=hashlib.sha256(raw).hexdigest()
        self.idx={"schema":1,"format":"DMNPACK2","release":"dmn-uk-test",
                  "tiles":{"213_-9":{"sha256":sha,"bytes":len(raw),"file":"dmn-test.dmnpack"}}}
        self.index.write_text(json.dumps(self.idx))
        self.obj={"schema":1,"issue":"CAT1-PUBLISHED-DMN-ANDROID-READER-01",
                  "scope":"published_stockport_piece_android_parser_only",
                  "tile_key":"213_-9","published_release":"dmn-uk-test",
                  "installed_sha256":sha,"installed_bytes":len(raw),
                  "node_count":100,"edge_count":150,
                  "reader":"DmnPack.read + DmnTiles.router",
                  "map_pack_worker_verified":False,"complete_uk_verified":False,
                  "offline_route_computed":False,"guidance_screen_verified":False,
                  "speech_verified":False,"independent_legal_truth_verified":False,
                  "release_allowed":False}
        self.save()

    def save(self):
        self.receipt.write_text(json.dumps(self.obj))

    def test_matching_receipt_is_parser_only(self):
        r=verify(self.receipt,self.index,self.piece)
        self.assertTrue(r["production_reader_passed"])
        self.assertFalse(r["offline_route_computed"])

    def test_no_receipt_fails(self):
        self.receipt.unlink()
        with self.assertRaisesRegex(ValueError,"MISSING"):
            verify(self.receipt,self.index,self.piece)

    def test_changed_installed_hash_fails(self):
        self.obj["installed_sha256"]="0"*64;self.save()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.index,self.piece)

    def test_claiming_worker_success_fails(self):
        self.obj["map_pack_worker_verified"]=True;self.save()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.index,self.piece)

    def test_tampered_published_tile_fails(self):
        self.piece.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError,"SOURCE_MISMATCH"):
            verify(self.receipt,self.index,self.piece)

    def test_zero_edges_fail(self):
        self.obj["edge_count"]=0;self.save()
        with self.assertRaisesRegex(ValueError,"EMPTY_GRAPH"):
            verify(self.receipt,self.index,self.piece)


    def test_installed_offline_route_receipt(self):
        self.obj = {"schema":1,
            "scope":"published_stockport_subset_tls_download_install_offline_route",
            "published_release":self.idx["release"],
            "installed_sha256":self.idx["tiles"]["213_-9"]["sha256"],
            "installed_bytes":self.piece.stat().st_size, "download_calls":3,
            "checksum_rejection_verified":True,"corruption_repair_verified":True,
            "offline_route_computed":True,"route_time_network_requests":0,
            "complete_uk_verified":False,"independent_legal_truth_verified":False,
            "route_metres":1200.0,"route_edges":18}
        self.save()
        self.assertTrue(verify_install_route(self.receipt,self.index,self.piece)["offline_route_computed"])
        for key, value in (("complete_uk_verified",True), ("route_time_network_requests",1),
                           ("route_edges",0), ("route_metres",float("nan")),
                           ("checksum_rejection_verified",False)):
            original = self.obj[key]
            self.obj[key] = value; self.save()
            with self.assertRaises(ValueError):
                verify_install_route(self.receipt,self.index,self.piece)
            self.obj[key] = original


    def test_four_nation_receipt_requires_authentic_bytes_and_each_real_route(self):
        from dmn_live_bytes import SQUARES
        assets = self.index.parent
        raw = gzip.compress(b"DMNPACK2-format-fixture-only")
        sha = hashlib.sha256(raw).hexdigest()
        idx = {"schema":1,"format":"DMNPACK2","release":"dmn-uk-20261029-22","tiles":{}}
        samples = {}
        for nation, square in SQUARES.items():
            idx["tiles"][square] = {"file":"dmn-"+square+".dmnpack","bytes":len(raw),"sha256":sha}
            (assets/("m02-"+nation+".dmnpack")).write_bytes(raw)
            samples[nation] = {"square":square,"installed_sha256":sha,"installed_bytes":len(raw),
                "node_count":10,"edge_count":20,"route_edges":2,"start_osm_way":123,"finish_osm_way":456,
                "start_edge":1,"finish_edge":2,"route_metres":10.0}
        self.index.write_text(json.dumps(idx))
        record = {"schema":1,"scope":"four_nation_published_tile_tls_install_graph_route_smoke",
            "published_release":idx["release"],"download_calls":4,"route_time_network_requests":0,
            "complete_uk_verified":False,"independent_legal_truth_verified":False,"samples":samples}
        self.receipt.write_text(json.dumps(record))
        self.assertEqual(4,len(verify_four_nation_routes(self.receipt,self.index,assets)["samples"]))
        bad = copy.deepcopy(record);bad["samples"].pop("wales_cardiff")
        self.receipt.write_text(json.dumps(bad))
        with self.assertRaisesRegex(ValueError,"INCOMPLETE"):
            verify_four_nation_routes(self.receipt,self.index,assets)
        for key,value in (("route_edges",0),("route_metres",float("nan")),("start_edge",999),
                          ("installed_sha256","0"*64)):
            bad=copy.deepcopy(record);bad["samples"]["scotland_edinburgh"][key]=value
            self.receipt.write_text(json.dumps(bad))
            with self.assertRaises(ValueError):
                verify_four_nation_routes(self.receipt,self.index,assets)
        self.receipt.write_text(json.dumps(record))
        (assets/"m02-northern_ireland_belfast.dmnpack").write_bytes(b"corrupt")
        with self.assertRaisesRegex(ValueError,"ASSET_MISMATCH"):
            verify_four_nation_routes(self.receipt,self.index,assets)


if __name__=="__main__":
    unittest.main()
