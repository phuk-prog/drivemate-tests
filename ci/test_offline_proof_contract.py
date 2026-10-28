"""Fail-closed offline acceptance evidence contract tests.

Only validator/ledger fixtures are synthetic; none is a fabricated road or an
assertion that the real UK offline navigation/download workflow has passed.
"""
import copy
import unittest
from process_contract import validate_acceptance


SHA = lambda c: c * 64


def record(mode="controlled_cache"):
    return {
        "app_sha": "a" * 40, "test_sha": "b" * 40,
        "map_sha256": SHA("c"), "input_sha256": SHA("d"),
        "case_id": "osm-way-930354", "country": "GB",
        "source_kind": "published_real_uk", "mode": mode,
        "claim": "pipeline_only", "legal_truth": {"status": "unknown"},
        "stages": {
            key: {"status": "passed", "artifact_sha256": SHA("e")}
            for key in ("source", "loader", "session", "screen")
        },
    }


def pinned_offline_record():
    item = record("offline")
    item["offline_proof"] = {
        "network": {
            "method": "emulator_network_namespace",
            "blocked_before": True,
            "blocked_after": True,
            "probe_sha256": SHA("f"),
        },
        "delivery": {
            "source": "published_release",
            "catalog_sha256": SHA("1"),
            "manifest_sha256": SHA("2"),
            "installed_sha256": item["input_sha256"],
            "read_sha256": item["input_sha256"],
            "install_verified_before_isolation": True,
            "read_after_isolation": True,
        },
    }
    return item


class OfflineProofContractTest(unittest.TestCase):
    def test_existing_controlled_cache_is_not_promoted_to_offline(self):
        self.assertEqual("pipeline_only", validate_acceptance(record())["scope"])

    def test_offline_label_alone_cannot_qualify_as_offline_acceptance(self):
        with self.assertRaisesRegex(ValueError, "OFFLINE_PROOF_REQUIRED"):
            validate_acceptance(record("offline"))

    def test_complete_pinned_offline_record_is_a_valid_ledger_entry_only(self):
        result = validate_acceptance(pinned_offline_record())
        self.assertEqual("offline_evidence_record_valid", result["scope"])
        self.assertEqual("evidence_record_valid", result["status"])

    def test_isolation_must_be_observed_before_and_after(self):
        for key in ("blocked_before", "blocked_after"):
            with self.subTest(key=key):
                item = pinned_offline_record()
                item["offline_proof"]["network"][key] = False
                with self.assertRaisesRegex(ValueError, "OFFLINE_NETWORK_NOT_VERIFIED"):
                    validate_acceptance(item)

    def test_network_probe_digest_is_required(self):
        item = pinned_offline_record()
        item["offline_proof"]["network"]["probe_sha256"] = "none"
        with self.assertRaisesRegex(ValueError, "OFFLINE_NETWORK_NOT_VERIFIED"):
            validate_acceptance(item)

    def test_local_installed_and_read_hashes_must_match_exact_source(self):
        for key in ("installed_sha256", "read_sha256"):
            with self.subTest(key=key):
                item = pinned_offline_record()
                item["offline_proof"]["delivery"][key] = SHA("9")
                with self.assertRaisesRegex(ValueError, "OFFLINE_ASSET_NOT_VERIFIED"):
                    validate_acceptance(item)

    def test_release_catalog_and_manifest_are_pinned(self):
        for key in ("catalog_sha256", "manifest_sha256"):
            with self.subTest(key=key):
                item = pinned_offline_record()
                item["offline_proof"]["delivery"][key] = None
                with self.assertRaisesRegex(ValueError, "OFFLINE_ASSET_NOT_VERIFIED"):
                    validate_acceptance(item)

    def test_claim_cannot_use_unverified_or_uninstalled_fixture_data(self):
        for key,value in (
            ("source","test_fixture"),
            ("install_verified_before_isolation",False),
            ("read_after_isolation",False),
        ):
            with self.subTest(key=key):
                item = pinned_offline_record()
                item["offline_proof"]["delivery"][key] = value
                with self.assertRaisesRegex(ValueError, "OFFLINE_ASSET_NOT_VERIFIED"):
                    validate_acceptance(item)

    def test_offline_proof_cannot_replace_session_or_screen(self):
        item = pinned_offline_record()
        del item["stages"]["session"]
        with self.assertRaisesRegex(ValueError, "MISSING_ACCEPTANCE_STAGE"):
            validate_acceptance(item)


if __name__ == "__main__":
    unittest.main()
