"""Frozen TF-04 publisher-vs-app release diagnostics: metadata fixtures ONLY.

No synthetic road data, live download claims, or navigable graph acceptance.
This test must fail until both repository endpoints are independently inspected.
"""
import json
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import patch

from real_offline_acceptance import public_release_lookup, release_target_inventory

APP = "phuk-prog/drivemate-data"
PUBLISHER = "phuk-prog/drivemate-map"
TAG = "routing-uk"


class DualRoutingReleaseTargets(TestCase):
    def test_known_publisher_repo_is_not_rejected_as_untrusted(self):
        fake = SimpleNamespace(returncode=1, stdout="HTTP/2 404\n\n")
        with patch("real_offline_acceptance.subprocess.run", return_value=fake) as execute:
            outcome = public_release_lookup(PUBLISHER, TAG)
        self.assertEqual("unavailable_or_inaccessible", outcome["status"])
        args = execute.call_args.args[0]
        self.assertEqual("repos/phuk-prog/drivemate-map/releases/tags/routing-uk", args[-1])
        self.assertFalse(outcome["public_verified"])

    def test_pair_404_is_not_a_claim_that_either_repo_does_not_exist(self):
        missing = {"status": "unavailable_or_inaccessible",
                   "public_verified": False, "confirmed_nonexistent": False}
        with patch("real_offline_acceptance.public_release_lookup", return_value=missing) as probe:
            report = release_target_inventory()
        self.assertEqual(2, probe.call_count)
        self.assertEqual([(APP, TAG), (PUBLISHER, TAG)],
                         [entry.args for entry in probe.call_args_list])
        self.assertFalse(report["app_target"]["public_verified"])
        self.assertFalse(report["publisher_target"]["public_verified"])
        self.assertFalse(report["app_graph_download_verified"])
        self.assertFalse(report["offline_acceptance"])
        self.assertTrue(report["source_repository_mismatch"])

    def test_publisher_manifest_metadata_cannot_validate_legacy_app_target(self):
        inaccessible = {"status": "unavailable_or_inaccessible",
                        "public_verified": False, "confirmed_nonexistent": False}
        metadata = {"status": "public_release_metadata_verified",
                    "public_verified": True, "confirmed_nonexistent": False,
                    "manifest_asset_bytes": 1024, "manifest_asset_sha256": "a" * 64}
        with patch("real_offline_acceptance.public_release_lookup",
                   side_effect=[inaccessible, metadata]):
            report = release_target_inventory()
        self.assertFalse(report["app_graph_download_verified"])
        self.assertFalse(report["offline_acceptance"])
        self.assertTrue(report["publisher_target"]["public_verified"])
        self.assertFalse(report["app_target"]["public_verified"])

    def test_unrelated_repository_or_tag_still_rejected(self):
        for repo, tag in (("other/unknown", TAG), (PUBLISHER, "latest"),
                          ("phuk-prog/drivemate-tests", TAG)):
            with self.subTest(repo=repo, tag=tag):
                with self.assertRaisesRegex(ValueError, "UNTRUSTED_RELEASE_TARGET"):
                    public_release_lookup(repo, tag)


if __name__ == "__main__":
    import unittest
    unittest.main()
