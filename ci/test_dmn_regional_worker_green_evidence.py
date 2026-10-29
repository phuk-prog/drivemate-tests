"""Candidate worker receipts must never hide missing, altered or overclaimed evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from dmn_regional_worker_green_evidence import verify

class TestDmnRegionalWorkerGreenEvidence(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        p=Path(self.tmp.name)
        self.receipt=p/"r.json";self.index=p/"i.json";self.catalog=p/"c.json"
        self.idx={"schema":1,"format":"DMNPACK2","release":"dmn-uk-test",
                  "tiles":{"213_-9":{"file":"dmn-test.dmnpack","sha256":"a"*64,"bytes":123}}}
        self.index.write_text(json.dumps(self.idx))
        self.cat={"test_source":{"kind":"actual_published_catalog_subset","catalog_sha256":"c"*64},
                  "regions":{"stockport":{"map":{"sha256":"b"*64,"bytes":456}}}}
        self.catalog.write_text(json.dumps(self.cat))
        self.record={
          "schema":1,"issue":"CAT1-REGIONAL-MAP-WORKER-GREEN-01",
          "scope":"same_stockport_region_real_worker_single_real_dmn_piece_candidate",
          "region_id":"stockport","published_catalog_sha256":"c"*64,
          "display_installed_sha256":"b"*64,"display_installed_bytes":456,
          "full_published_index_sha256":hashlib.sha256(self.index.read_bytes()).hexdigest(),
          "published_dmn_release":"dmn-uk-test","published_piece_file":"dmn-test.dmnpack",
          "published_piece_sha256":"a"*64,"published_piece_bytes":123,
          "installed_dmn_index_scope":"single_tile_bounded_subset",
          "routing_index_installed":True,"one_published_dmn_piece_installed":True,
          "dmn_offline_for_two_points":"READY","worker_result":"SUCCESS",
          "node_count":9,"edge_count":7,
          "whole_uk_install_verified":False,"offline_route_computed":False,
          "screen_or_speech_verified":False,
          "independent_legal_truth_verified":False,"release_allowed":False
        }
        self.write()

    def write(self):
        self.receipt.write_text(json.dumps(self.record))

    def test_single_real_piece_receipt(self):
        v=verify(self.receipt,self.catalog,self.index)
        self.assertTrue(v["production_worker_graph_install_passed"])
        self.assertFalse(v["full_uk_install_verified"])
    def test_missing_receipt(self):
        self.receipt.unlink()
        with self.assertRaisesRegex(ValueError,"MISSING"):
            verify(self.receipt,self.catalog,self.index)
    def test_mismatched_file(self):
        self.record["published_piece_sha256"]="z"*64;self.write()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)
    def test_false_full_uk_success(self):
        self.record["whole_uk_install_verified"]=True;self.write()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)
    def test_false_offline_session(self):
        self.record["offline_route_computed"]=True;self.write()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)
    def test_bad_index_identity(self):
        self.idx["release"]="dmn-uk-changed"
        self.index.write_text(json.dumps(self.idx))
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)

if __name__=="__main__":
    unittest.main()
