"""Regression: genuine Android offline component receipts stay evidence, not acceptance."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import collect_results


class RealOfflineComponentReceiptsTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.source=self.root/"stockport-live-region.json"
        self.entry={"id":"stockport","map":{
            "tag":"map-data-region-stockport-r1-a1","bytes":12,
            "sha256":"b"*64}}
        self.source.write_text(json.dumps({
            "test_source":{"catalog_sha256":"a"*64,"kind":"actual_published_catalog_subset"},
            "regions":{"stockport":self.entry}}))
        self.installed={"schema":1,"scope":"published_stockport_display_install_only",
                        "published_catalog_sha256":"a"*64,
                        "region_id":"stockport",
                        "published_map_tag":self.entry["map"]["tag"],
                        "installed_sha256":"b"*64,
                        "installed_bytes":12,"production_installer":"RegionPacks.sync",
                        "offline_route_accepted":False,"screen_guidance_accepted":False}
        self.route={"schema":1,"scope":"bundled_greater_manchester_offline_route_only",
                    "graph_source":"bundled_app_asset_not_published_whole_uk",
                    "network_blocked_before_route":True,
                    "network_blocked_after_route":True,
                    "route_points":2,"route_steps":1,
                    "published_whole_uk_graph_verified":False,
                    "production_guidance_screen_verified":False,
                    "legal_truth_verified":False,
                    "full_offline_acceptance":False}

    def verify(self,name,data):
        path=self.root/(name+"-receipt.json")
        path.write_text(json.dumps(data))
        self.assertTrue(callable(getattr(collect_results,"verify_real_offline_component_receipt",None)),
                        "REAL_ANDROID_RECEIPT_VERIFIER_MISSING")
        return collect_results.verify_real_offline_component_receipt(name,path,self.source)

    def test_real_published_stockport_install_receipt_matches_original_catalog(self):
        result=self.verify("publishedStockportInstalledByProductionRegionDownloader",self.installed)
        self.assertEqual("published_stockport_display_install_only",result["scope"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_installed_wrong_hash_fails_not_published_install(self):
        self.installed["installed_sha256"]="0"*64
        with self.assertRaisesRegex(ValueError,"OFFLINE_COMPONENT_SOURCE_MISMATCH"):
            self.verify("publishedStockportInstalledByProductionRegionDownloader",self.installed)

    def test_no_production_install_receipt_does_not_count(self):
        self.installed["production_installer"]="test_copy_fixture"
        with self.assertRaisesRegex(ValueError,"OFFLINE_COMPONENT_SOURCE_MISMATCH"):
            self.verify("publishedStockportInstalledByProductionRegionDownloader",self.installed)

    def test_bundled_gm_route_receipt_never_promotes_to_whole_uk(self):
        result=self.verify("bundledManchesterRouterActuallyComputesWithoutNetwork",self.route)
        self.assertEqual("bundled_greater_manchester_offline_route_only",result["scope"])
        self.assertFalse(result["offline_navigation_accepted"])

    def test_missing_before_after_network_proof_fails_closed(self):
        self.route["network_blocked_after_route"]=False
        with self.assertRaisesRegex(ValueError,"OFFLINE_COMPONENT_NETWORK_UNVERIFIED"):
            self.verify("bundledManchesterRouterActuallyComputesWithoutNetwork",self.route)

    def test_invented_screen_acceptance_is_rejected(self):
        self.route["production_guidance_screen_verified"]=True
        with self.assertRaisesRegex(ValueError,"OFFLINE_COMPONENT_SCOPE_INVALID"):
            self.verify("bundledManchesterRouterActuallyComputesWithoutNetwork",self.route)

    def test_invented_whole_uk_acceptance_is_rejected(self):
        self.installed["offline_route_accepted"]=True
        with self.assertRaisesRegex(ValueError,"OFFLINE_COMPONENT_SCOPE_INVALID"):
            self.verify("publishedStockportInstalledByProductionRegionDownloader",self.installed)


if __name__=="__main__":
    unittest.main()
