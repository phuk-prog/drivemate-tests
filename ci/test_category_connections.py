"""Frozen regressions for category-specific endpoint inventory.

URL snippets are configuration-only test fixtures; no fake road layouts, real
network requests, APK success, or full-UK offline acceptance are manufactured.
"""
import json
from pathlib import Path
import tempfile
import unittest

from category_connections import (
    audit, classify_source_text, classify_url, source_role,
)


def programme():
    return {
        "schema": 1, "execution_order": list(range(1, 25)),
        "categories": [{"id": i, "name": "Category " + str(i)} for i in range(1, 25)],
        "batches": [
            {"id": "A", "categories": [1, 2, 4]},
            {"id": "B", "categories": [3, 5, 6, 7, 11]},
        ],
    }


class CategoryConnectionContracts(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.appdir = self.root / "app/src/main/java/com/drivemate/app/data"
        self.appdir.mkdir(parents=True)
        (self.root / "app/build.gradle.kts").write_text(
            'val mapBase="https://github.com/phuk-prog/drivemate-map/releases/download"\n'
        )
        self.road = self.appdir / "RoadPack.kt"
        self.road.write_text(
            'private const val BASE = "https://github.com/phuk-prog/drivemate-data/releases/download/routing-uk"\n'
        )
        self.backup = self.root / "app/src/main/java/com/drivemate/app/account/DriveAppDataBackup.kt"
        self.backup.parent.mkdir(parents=True)
        self.backup.write_text('val url="https://www.googleapis.com/drive/v3/files"\n')

    def run_audit(self, batch="all", alignment=None):
        return audit(self.root, programme(), batch=batch, alignment=alignment)

    def test_all_24_current_categories_preserved_in_order(self):
        report = self.run_audit()
        self.assertEqual([str(i) for i in range(1, 25)], list(report["categories"]))
        self.assertEqual("no_static_url_observed", report["categories"]["21"]["status"])
        self.assertFalse(report["apk_binary_inspected"])
        self.assertFalse(report["runtime_network_verified"])

    def test_old_routing_repository_blocks_correct_categories(self):
        report = self.run_audit()
        self.assertEqual("blocked_needs_investigation", report["categories"]["1"]["status"])
        self.assertEqual("blocked_needs_investigation", report["categories"]["2"]["status"])
        self.assertIn("old_or_unreachable_project_target", report["categories"]["2"]["finding_codes"])
        self.assertEqual("static_inventory_only", report["categories"]["20"]["status"])

    def test_category_batch_does_not_claim_unselected_checks(self):
        report = self.run_audit(batch="A")
        self.assertEqual("not_selected", report["categories"]["20"]["status"])
        self.assertEqual(0, report["categories"]["20"]["references"])
        self.assertEqual("blocked_needs_investigation", report["categories"]["2"]["status"])

    def test_alignment_receipt_is_a_category1_and_2_blocker_not_a_success(self):
        receipt = {"scope": "checked_out_source_routing_publisher_alignment_only",
                   "offline_acceptance": False,
                   "reason": "routing_release_repository_mismatch"}
        report = self.run_audit(alignment=receipt)
        self.assertIn("routing_release_repository_mismatch", report["categories"]["1"]["finding_codes"])
        self.assertIn("routing_release_repository_mismatch", report["categories"]["2"]["finding_codes"])

    def test_source_text_classifies_external_host_without_claiming_live(self):
        items = classify_source_text(
            "app/src/main/java/com/drivemate/app/data/TomTom.kt",
            'val url = "https://api.tomtom.com/traffic/services/5/incidentDetails?key=$key"'
        )
        self.assertEqual([19], items[0]["categories"])
        self.assertEqual("api.tomtom.com", items[0]["host"])
        self.assertEqual("static_reference_only", items[0]["status"])
        self.assertNotIn("url", items[0])  # never print a potential token/secret

    def test_unknown_production_connection_is_not_silently_green(self):
        extra = self.root / "app/src/main/java/com/drivemate/app/Unexpected.kt"
        extra.write_text('val api = "https://unrecognized.example.net/service"\n')
        report = self.run_audit()
        self.assertIn("unassigned_source_needs_review", report["categories"]["20"]["finding_codes"])
        self.assertEqual("blocked_needs_investigation", report["categories"]["20"]["status"])

    def test_comments_do_not_create_fake_active_downloads(self):
        items = classify_source_text(
            "app/src/main/java/com/drivemate/app/data/RoadPack.kt",
            '// https://github.com/phuk-prog/DriveMate/releases/download/routing-uk\n'
            '/* https://github.com/phuk-prog/DriveMate/releases/download/routing-uk */\n'
        )
        self.assertEqual([], items)

    def test_unencrypted_http_is_flagged(self):
        item = classify_url("http://api.example.org/v1")
        self.assertEqual("insecure_http_reference", item["status"])

    def test_osm_sandbox_is_not_assumed_production(self):
        item = classify_url("https://master.apis.dev.openstreetmap.org/api/0.6")
        self.assertEqual("test_service_reference_not_live", item["status"])

    def test_bad_alignment_receipt_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "INVALID_ROUTING_ALIGNMENT_RECEIPT"):
            self.run_audit(alignment={"scope": "wrong", "offline_acceptance": True})

    def test_unknown_batch_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "UNKNOWN_TEST_BATCH"):
            self.run_audit(batch="Z")

    def test_real_mapping_for_backup_and_source_roles(self):
        self.assertEqual([20], source_role(
            "app/src/main/java/com/drivemate/app/account/DriveAppDataBackup.kt")[0])
        self.assertEqual([23], source_role(
            "app/src/main/java/com/drivemate/app/osm/OsmConnection.kt")[0])
        self.assertEqual([24], source_role(
            "app/src/main/java/com/drivemate/app/data/FuelPrices.kt")[0])

    def test_malformed_ipv6_is_invalid_without_disclosing_token(self):
        for value in ("https://[broken", "https://[not-ip]/?token=private"):
            with self.subTest(value=value):
                item = classify_url(value)
                self.assertEqual("invalid_url", item["status"])
                self.assertEqual("", item["host"])
                self.assertNotIn("private", json.dumps(item))

    def test_valid_bare_ipv6_keeps_closing_bracket(self):
        item = classify_url("https://[::1]")
        self.assertEqual("::1", item["host"])
        self.assertEqual("static_reference_only", item["status"])

    def test_invalid_port_is_not_a_valid_static_reference(self):
        for value in ("https://example.org:wrong/", "https://example.org:99999/"):
            with self.subTest(value=value):
                self.assertEqual("invalid_url", classify_url(value)["status"])

    def test_invalid_literal_does_not_prevent_category_report(self):
        self.road.write_text('val url = "https://[broken"\n')
        result = self.run_audit()
        self.assertIn("invalid_url", result["categories"]["1"]["finding_codes"])
        self.assertEqual(24, len(result["categories"]))
        self.assertFalse(result["runtime_network_verified"])

    def test_missing_main_source_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "PRODUCTION_APP_SOURCES_MISSING"):
                audit(temp, programme())


    def test_tf02_category_counts_are_explicit_not_just_blocked_flags(self):
        report = self.run_audit()
        c1 = report["categories"]["1"]
        self.assertEqual(2, c1["references"])
        self.assertEqual(1, c1["reference_status_counts"]["old_or_unreachable_project_target"])
        self.assertEqual(1, c1["reference_status_counts"]["static_reference_only"])
        self.assertEqual(2, c1["ownership_unverified_references"])
        self.assertEqual(1, c1["unresolved_references"])
        self.assertEqual(0, report["categories"]["23"]["unresolved_references"])
        self.assertFalse(report["runtime_network_verified"])

    def test_tf02_test_server_and_unknown_owner_are_counted_without_live_claim(self):
        osm = self.root / "app/src/main/java/com/drivemate/app/osm/OsmConnection.kt"
        osm.parent.mkdir(parents=True, exist_ok=True)
        osm.write_text('val test = "https://master.apis.dev.openstreetmap.org/api/0.6"\\n')
        unexpected = self.root / "app/src/main/java/com/drivemate/app/Unexpected.kt"
        unexpected.write_text('val api = "https://unknown.example.net/v1"\\n')
        report = self.run_audit()
        self.assertEqual(1, report["categories"]["23"]["reference_status_counts"]["test_service_reference_not_live"])
        self.assertEqual(1, report["categories"]["23"]["unresolved_references"])
        self.assertEqual(1, report["categories"]["20"]["unassigned_references"])
        self.assertEqual(2, report["categories"]["20"]["ownership_unverified_references"])
        self.assertFalse(report["categories"]["23"]["live_http_verified"])

    def test_tf02_batch_counts_exclude_unselected_categories(self):
        report = self.run_audit(batch="A")
        self.assertEqual({}, report["categories"]["20"]["reference_status_counts"])
        self.assertEqual(0, report["categories"]["20"]["ownership_unverified_references"])
        self.assertEqual(0, report["categories"]["20"]["unresolved_references"])
        self.assertEqual(1, report["categories"]["2"]["unresolved_references"])

    def test_review_location_links_belong_to_current_map_corrections_category(self):
        records = classify_source_text(
            "app/src/main/java/com/drivemate/app/ui/ReviewLocationMap.kt",
            'fun osmUrl() = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude"\n'
            'fun panoramaxUrl() = "https://panoramax.fr/#focus=map&map=18/$latitude/$longitude"',
        )
        self.assertEqual(2, len(records))
        self.assertEqual({"www.openstreetmap.org", "panoramax.fr"}, {r["host"] for r in records})
        for record in records:
            self.assertEqual([23], record["categories"])
            self.assertEqual("assigned", record["category_assignment"])

    def test_review_location_mapping_removes_only_unassigned_reliability_finding(self):
        review = self.root / "app/src/main/java/com/drivemate/app/ui/ReviewLocationMap.kt"
        review.parent.mkdir(parents=True)
        review.write_text(
            'fun osmUrl() = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude"\n'
            'fun panoramaxUrl() = "https://panoramax.fr/#focus=map&map=18/$latitude/$longitude"\n'
        )
        report = self.run_audit()
        self.assertEqual(0, report["categories"]["20"]["unassigned_references"])
        self.assertEqual(2, report["categories"]["23"]["references"])
        self.assertEqual(2, report["categories"]["23"]["ownership_unverified_references"])
        self.assertFalse(report["categories"]["23"]["live_http_verified"])
        self.assertFalse(report["runtime_network_verified"])
        self.assertEqual("not_selected", self.run_audit(batch="A")["categories"]["23"]["status"])


if __name__ == "__main__":
    unittest.main()
