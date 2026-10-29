"""Fail-closed receipt regressions for authentic one-piece DMN route pretest."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from dmn_stockport_route_evidence import verify


class RouteReceiptTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root=Path(self.tmp.name)
        self.piece=root/"piece.dmnpack"
        self.piece.write_bytes(b"published-byte-fixture")
        self.idx=root/"index.json"
        self.record=root/"receipt.json"
        sha=hashlib.sha256(self.piece.read_bytes()).hexdigest()
        self.idx.write_text(json.dumps({
            "schema":1,"format":"DMNPACK2","release":"fixture",
            "tiles":{"213_-9":{"file":"dmn-213_-9.dmnpack","sha256":sha,"bytes":self.piece.stat().st_size}}
        }))
        self.value={
            "schema":1,"issue":"CAT1-PUBLISHED-DMN-ROUTE-01",
            "scope":"published_stockport_single_piece_android_router_plan_only",
            "tile_key":"213_-9","published_release":"fixture",
            "installed_sha256":sha,"installed_bytes":self.piece.stat().st_size,
            "node_count":300,"edge_count":600,
            "start_lat":53.4051,"start_lon":-2.1630,
            "finish_lat":53.4106,"finish_lon":-2.1591,
            "start_edge":5,"finish_edge":20,"planned_edges":12,
            "planned_metres":950.0,"planned_seconds":92.0,
            "route_contiguous_and_turns_checked":True,"dmn_route_computed":True,
            "map_pack_worker_installed":False,"whole_uk_route_verified":False,
            "device_network_isolated":False,"guidance_screen_verified":False,
            "speech_verified":False,"independent_legal_truth_verified":False,
            "release_allowed":False,
        }
        self.save()

    def save(self):
        self.record.write_text(json.dumps(self.value))

    def check(self):
        return verify(self.record,self.idx,self.piece)

    def rejects(self):
        with self.assertRaises(ValueError): self.check()

    def test_valid_bounded_receipt_does_not_claim_acceptance(self):
        data=self.check()
        self.assertTrue(data["production_dmn_route_computed"])
        for key in ("release_allowed","whole_uk_route_verified","network_isolated",
                    "guidance_screen_verified","speech_verified"):
            self.assertFalse(data[key])

    def test_missing_receipt_rejected(self):
        self.record.unlink()
        self.rejects()

    def test_replaced_published_bytes_rejected(self):
        self.piece.write_bytes(b"altered-published-byte-fixture")
        self.rejects()

    def test_fake_worker_or_release_acceptance_rejected(self):
        self.value["map_pack_worker_installed"]=True
        self.save()
        self.rejects()
        self.value["map_pack_worker_installed"]=False
        self.value["release_allowed"]=True
        self.save()
        self.rejects()

    def test_false_route_flags_rejected(self):
        self.value["dmn_route_computed"]=False
        self.save()
        self.rejects()
        self.value["dmn_route_computed"]=True
        self.value["device_network_isolated"]=True
        self.save()
        self.rejects()

    def test_invalid_route_result_rejected(self):
        self.value["planned_edges"]=0
        self.save()
        self.rejects()
        self.value["planned_edges"]=1
        self.value["planned_seconds"]=float("nan")
        self.save()
        self.rejects()

    def test_wrong_coordinates_and_scope_rejected(self):
        self.value["finish_lat"]=53.4201
        self.save()
        self.rejects()
        self.value["finish_lat"]=53.4106
        self.value["scope"]="full_uk_offline"
        self.save()
        self.rejects()


if __name__=="__main__":
    unittest.main()
