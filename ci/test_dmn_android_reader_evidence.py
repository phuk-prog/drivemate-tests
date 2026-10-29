"""Evidence must fail closed on missing, mismatched or overstated DMN parser receipts."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from dmn_android_reader_evidence import verify


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


if __name__=="__main__":
    unittest.main()
