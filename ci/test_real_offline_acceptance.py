"""Frozen prerequisite acceptance contracts for real UK offline navigation.

Only input contract fixtures are synthetic; no synthetic roads, false routes or
claimed installed PMTiles. Missing real graph release must BLOCK acceptance.
"""
import unittest
import importlib

class RealOfflineAcceptanceContract(unittest.TestCase):
    def setUp(self):
        try:
            self.module=importlib.import_module("real_offline_acceptance")
        except ModuleNotFoundError:
            self.fail("REAL_OFFLINE_ACCEPTANCE_GATES_MISSING")

    def state(self, **changes):
        base={"published_catalog_hash_verified":True,
              "region_pmtiles_bytes_verified":True,
              "app_region_cache_install_verified":True,
              "network_isolation_before_and_after":True,
              "routing_graph_public_release_verified":True,
              "graph_installed_in_production_store":True,
              "production_offline_route_computed":True,
              "production_guidance_screen_rendered":True,
              "independent_route_truth_verified":True}
        base.update(changes)
        return self.module.evaluate(base)

    def test_all_independent_stages_required_to_accept(self):
        a=self.state()
        self.assertEqual("passed",a["status"])
        self.assertTrue(a["accepted"])

    def test_published_map_hash_alone_cannot_satisfy_route(self):
        a=self.state(routing_graph_public_release_verified=False,
                     graph_installed_in_production_store=False,
                     production_offline_route_computed=False,
                     production_guidance_screen_rendered=False)
        self.assertFalse(a["accepted"])
        self.assertIn("routing_graph_public_release_verified",a["failed_stages"])

    def test_public_graph_release_404_blocks_not_passes(self):
        a=self.state(routing_graph_public_release_verified=False)
        self.assertEqual("blocked",a["status"])

    def test_unavailable_external_graph_is_unknown_not_a_nonexistent_graph_assertion(self):
        a=self.module.release_readiness(404)
        self.assertEqual("unavailable_or_inaccessible",a["status"])
        self.assertFalse(a["public_verified"])
        self.assertFalse(a["confirmed_nonexistent"])

    def test_200_requires_real_manifest_content_and_digest(self):
        a=self.module.release_readiness(200, assets=[])
        self.assertFalse(a["public_verified"])
        self.assertEqual("manifest_missing",a["status"])

    def test_hidden_network_or_stage_claims_fail_closed(self):
        a=self.state(network_isolation_before_and_after=None)
        self.assertFalse(a["accepted"])
        self.assertIn("network_isolation_before_and_after",a["unknown_stages"])

    def test_synthetic_or_bundled_graph_cannot_masquerade_as_published_uk(self):
        a=self.module.evaluate({
            "published_catalog_hash_verified":True,
            "region_pmtiles_bytes_verified":True,
            "built_in_greater_manchester_route_computed":True,
        })
        self.assertEqual("blocked",a["status"])
        self.assertFalse(a["accepted"])

    def test_unsafe_truth_claim_cannot_be_autofilled(self):
        a=self.state(independent_route_truth_verified=False)
        self.assertFalse(a["accepted"])
        self.assertIn("independent_route_truth_verified",a["failed_stages"])

    def test_no_screen_evidence_never_passes(self):
        a=self.state(production_guidance_screen_rendered=False)
        self.assertEqual("blocked",a["status"])

    def test_returns_frozen_ordered_stage_contract(self):
        a=self.state()
        self.assertEqual(9,len(a["stages"]))
        self.assertIn("app_region_cache_install_verified",a["stages"])


if __name__=="__main__":
    unittest.main()
