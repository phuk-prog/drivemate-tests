"""Guard real worker RED receipt against missing/misrepresented source evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from dmn_regional_worker_evidence import verify


class TestRegionalWorkerEvidence(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root=Path(self.tmp.name)
        self.catalog=root/"stockport.json"
        self.index=root/"dmn.json"
        self.receipt=root/"receipt.json"
        self.idx={"schema":1,"format":"DMNPACK2","release":"dmn-uk-test",
                  "tiles":{"213_-9":{"sha256":"a"*64,"bytes":16,"file":"dmn-test.dmnpack"}}}
        self.index.write_text(json.dumps(self.idx))
        self.cat={"test_source":{"kind":"actual_published_catalog_subset","catalog_sha256":"c"*64},
                  "regions":{"stockport":{"map":{"sha256":"b"*64,"bytes":200}}}}
        self.catalog.write_text(json.dumps(self.cat))
        self.record={
            "schema":1,"issue":"CAT1-REGIONAL-MAP-WORKER-RED-01",
            "scope":"real_worker_one_published_stockport_display_bounded_empty_weekly_companions",
            "published_catalog_sha256":"c"*64,"region_id":"stockport",
            "display_installed_sha256":"b"*64,"display_installed_bytes":200,
            "published_dmn_index_sha256":hashlib.sha256(self.index.read_bytes()).hexdigest(),
            "published_dmn_release":"dmn-uk-test",
            "regional_snapshot_selected":True,"weekly_companion_files_in_bounded_fixture":0,
            "worker_result":"SUCCESS","dmn_index_installed_after_worker":False,
            "dmn_offline_after_worker":"MISSING",
            "observed_bug":"REGIONAL_MAP_WORKER_SUCCESS_WITHOUT_DMN_GRAPH",
            "real_world_full_catalog_verified":False,"offline_navigation_verified":False,
            "screen_or_speech_verified":False,"release_allowed":False
        }
        self.save()

    def save(self):
        self.receipt.write_text(json.dumps(self.record))

    def test_real_observation_preserves_red_status(self):
        v=verify(self.receipt,self.catalog,self.index)
        self.assertTrue(v["production_worker_observed"])
        self.assertFalse(v["dmn_graph_installed"])

    def test_missing_receipt_fails(self):
        self.receipt.unlink()
        with self.assertRaisesRegex(ValueError,"MISSING"):
            verify(self.receipt,self.catalog,self.index)

    def test_false_graph_install_fails(self):
        self.record["dmn_index_installed_after_worker"]=True;self.save()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)

    def test_false_full_catalog_fails(self):
        self.record["real_world_full_catalog_verified"]=True;self.save()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)

    def test_wrong_display_sha_fails(self):
        self.record["display_installed_sha256"]="0"*64;self.save()
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)

    def test_changed_index_fails(self):
        self.idx["release"]="dmn-uk-other"
        self.index.write_text(json.dumps(self.idx))
        with self.assertRaisesRegex(ValueError,"MISMATCH"):
            verify(self.receipt,self.catalog,self.index)


if __name__=="__main__":
    unittest.main()
